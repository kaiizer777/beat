package com.beat.service;

import com.beat.dto.RawArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ResearchPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipelineService.class);

    @Value("${digest.freshness.max-age-hours:168}")
    private int maxAgeHours;

    private final TinyFishClient tinyFishClient;
    private final TinyFishFetchClient fetchClient;
    private final ArticleDeduplicationService deduplicationService;

    public ResearchPipelineService(TinyFishClient tinyFishClient,
                                   TinyFishFetchClient fetchClient,
                                   ArticleDeduplicationService deduplicationService) {
        this.tinyFishClient = tinyFishClient;
        this.fetchClient = fetchClient;
        this.deduplicationService = deduplicationService;
    }

    public ResearchResult executeResearch(String topicQuery) {
        if (topicQuery == null || topicQuery.isBlank()) {
            log.warn("Empty topicQuery received for research pipeline");
            return new ResearchResult(List.of(), Map.of("error", "empty topicQuery"));
        }

        // Use LinkedHashMap to preserve insertion order in the final JSON-style log line.
        Map<String, Object> metrics = new LinkedHashMap<>();

        log.info("Starting Phase 3 Research Pipeline for topic: '{}'", topicQuery);

        // 1. Generate 3-5 deterministic sub-queries
        List<String> subQueries = generateSubQueries(topicQuery);
        log.info("Generated {} sub-queries: {}", subQueries.size(), subQueries);
        metrics.put("subQueryCount", subQueries.size());

        // 2. Execute TinyFish Search for each sub-query in sequence
        List<RawArticle> rawCandidateList = new ArrayList<>();
        Map<String, Integer> perSubQueryCounts = new LinkedHashMap<>();
        int totalSubQueriesWithZero = 0;
        for (String subQuery : subQueries) {
            log.info("Executing Search sub-query: '{}'", subQuery);
            List<TinyFishClient.SearchResultItem> searchItems = tinyFishClient.searchNews(subQuery);
            log.info("Sub-query '{}' returned {} items", subQuery, searchItems.size());
            perSubQueryCounts.put(subQuery, searchItems.size());
            if (searchItems.isEmpty()) {
                totalSubQueriesWithZero++;
            }

            for (TinyFishClient.SearchResultItem item : searchItems) {
                RawArticle rawArticle = new RawArticle(
                        item.getTitle(),
                        item.getUrl(),
                        item.getSnippet(),
                        item.getPublisher(),
                        item.getDate(),
                        null,
                        null
                );
                rawCandidateList.add(rawArticle);
            }

            // Rate-limit pause (1000ms delay between sub-queries to stay within 30 RPM limit)
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Total raw candidates collected across sub-queries: {}", rawCandidateList.size());
        metrics.put("rawCandidatesTotal", rawCandidateList.size());
        metrics.put("perSubQueryCounts", perSubQueryCounts);
        metrics.put("subQueriesWithZeroResults", totalSubQueriesWithZero);

        // Phase 2: Freshness Filter
        Instant cutoff = Instant.now().minus(maxAgeHours, ChronoUnit.HOURS);
        int initialSize = rawCandidateList.size();

        int droppedNullOrUnparseable = 0;
        int droppedStale = 0;
        for (RawArticle a : rawCandidateList) {
            Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
            if (pub == null) {
                droppedNullOrUnparseable++;
            } else if (!pub.isAfter(cutoff)) {
                droppedStale++;
            }
        }

        rawCandidateList = rawCandidateList.stream()
                .filter(a -> {
                    Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
                    return pub != null && pub.isAfter(cutoff);
                })
                .collect(Collectors.toList());
        int dropped = initialSize - rawCandidateList.size();
        log.info("Freshness filter: kept {} / dropped {} (nullOrUnparseable={}, stale={})",
                rawCandidateList.size(), dropped, droppedNullOrUnparseable, droppedStale);
        metrics.put("freshnessKept", rawCandidateList.size());
        metrics.put("freshnessDroppedTotal", dropped);
        metrics.put("freshnessDroppedNullOrUnparseable", droppedNullOrUnparseable);
        metrics.put("freshnessDroppedStale", droppedStale);

        // Phase 3: Pre-Deduplication Sort by Date (newest first)
        rawCandidateList.sort(Comparator.comparing(
                (RawArticle a) -> com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt()),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        // 3. Early deduplication pass on raw candidates before fetching full text
        int preDedupSize = rawCandidateList.size();
        List<RawArticle> deduplicatedCandidates = deduplicationService.deduplicate(rawCandidateList);
        log.info("Candidate pool size after initial deduplication: {} (dropped {})",
                deduplicatedCandidates.size(), preDedupSize - deduplicatedCandidates.size());
        metrics.put("afterPreFetchDedup", deduplicatedCandidates.size());
        metrics.put("preFetchDedupDropped", preDedupSize - deduplicatedCandidates.size());

        // Limit maximum candidate fetches per run to prevent excessive overhead.
        // Raised to 50 so digest runs with targetCount up to 15 have headroom after
        // freshness filter + dedup + fact-checker rejection (~30% drop).
        int fetchLimit = Math.min(deduplicatedCandidates.size(), 50);
        List<RawArticle> fetchedArticles = new ArrayList<>();

        // 4. Fetch full text for candidates using TinyFish Fetch + Jina fallback
        int tinyFishCount = 0;
        int jinaCount = 0;
        int failedCount = 0;

        for (int i = 0; i < fetchLimit; i++) {
            RawArticle candidate = deduplicatedCandidates.get(i);
            log.info("Fetching full text ({}/{}): {}", i + 1, fetchLimit, candidate.getUrl());

            TinyFishFetchClient.FetchResult fetchResult = fetchClient.fetchContent(candidate.getUrl());
            if (fetchResult != null && fetchResult.getContent() != null && !fetchResult.getContent().isBlank()) {
                candidate.setFullText(fetchResult.getContent());
                candidate.setFetchSource(fetchResult.getSource());
                fetchedArticles.add(candidate);
                if ("tinyfish".equalsIgnoreCase(fetchResult.getSource())) {
                    tinyFishCount++;
                } else {
                    jinaCount++;
                }
            } else {
                failedCount++;
                log.warn("Skipping candidate due to failed full-text extraction: {}", candidate.getUrl());
            }

            // Small delay between fetch requests
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Fetch stage metrics: Total Attempted={}, TinyFish Success={}, Jina Fallback Success={}, Failed={}",
                fetchLimit, tinyFishCount, jinaCount, failedCount);
        metrics.put("fetchAttempted", fetchLimit);
        metrics.put("fetchSucceededTotal", tinyFishCount + jinaCount);
        metrics.put("fetchTinyfishSuccess", tinyFishCount);
        metrics.put("fetchJinaSuccess", jinaCount);
        metrics.put("fetchFailed", failedCount);

        // 5. Final deduplication pass to ensure clean output
        int beforeFinalDedup = fetchedArticles.size();
        List<RawArticle> finalPool = deduplicationService.deduplicate(fetchedArticles);
        log.info("Phase 3 Research Pipeline completed. Final pool size: {} articles (final dedup dropped {}) for topic '{}'",
                finalPool.size(), beforeFinalDedup - finalPool.size(), topicQuery);
        metrics.put("finalPoolSize", finalPool.size());
        metrics.put("finalDedupDropped", beforeFinalDedup - finalPool.size());

        return new ResearchResult(finalPool, metrics);
    }

    private List<String> generateSubQueries(String topic) {
        String trimmed = topic.trim();
        List<String> queries = new ArrayList<>();
        // Base topic unchanged so the search engine applies its default relevance ranking.
        queries.add(trimmed);
        // Generic "news" qualifier — broadens coverage without forcing a strict
        // temporal phrase that breaks the upstream search API.
        queries.add(trimmed + " news");
        // Current-year anchor biases results toward recent content; the Java
        // Freshness Filter enforces the exact temporal window downstream.
        String currentYear = String.valueOf(Year.now().getValue());
        queries.add(trimmed + " " + currentYear);
        // Semantic qualifiers that pull in different article classes (analytical
        // pieces, formal reports) the bare "news" query often misses. Critical
        // for reaching larger targetCounts (e.g. 10-15) where the default 3
        // sub-queries don't produce enough unique raw candidates.
        queries.add(trimmed + " analysis");
        queries.add(trimmed + " report");
        return queries;
    }
}
