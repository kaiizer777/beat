package com.beat.service;

import com.beat.dto.RawArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

    public List<RawArticle> executeResearch(String topicQuery) {
        if (topicQuery == null || topicQuery.isBlank()) {
            log.warn("Empty topicQuery received for research pipeline");
            return List.of();
        }

        log.info("Starting Phase 3 Research Pipeline for topic: '{}'", topicQuery);

        // 1. Generate 3-5 deterministic sub-queries
        List<String> subQueries = generateSubQueries(topicQuery);
        log.info("Generated {} sub-queries: {}", subQueries.size(), subQueries);

        // 2. Execute TinyFish Search for each sub-query in sequence
        List<RawArticle> rawCandidateList = new ArrayList<>();
        for (String subQuery : subQueries) {
            log.info("Executing Search sub-query: '{}'", subQuery);
            List<TinyFishClient.SearchResultItem> searchItems = tinyFishClient.searchNews(subQuery);
            log.info("Sub-query '{}' returned {} items", subQuery, searchItems.size());

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

        // Phase 2: Freshness Filter
        Instant cutoff = Instant.now().minus(maxAgeHours, ChronoUnit.HOURS);
        int initialSize = rawCandidateList.size();
        rawCandidateList = rawCandidateList.stream()
                .filter(a -> {
                    Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
                    return pub != null && pub.isAfter(cutoff);
                })
                .collect(Collectors.toList());
        int dropped = initialSize - rawCandidateList.size();
        log.debug("Freshness filter dropped {} stale articles", dropped);

        // Phase 3: Pre-Deduplication Sort by Date (newest first)
        rawCandidateList.sort(Comparator.comparing(
                (RawArticle a) -> com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt()),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        // 3. Early deduplication pass on raw candidates before fetching full text
        List<RawArticle> deduplicatedCandidates = deduplicationService.deduplicate(rawCandidateList);
        log.info("Candidate pool size after initial deduplication: {}", deduplicatedCandidates.size());

        // Limit maximum candidate fetches per run to prevent excessive overhead (e.g. max 35 articles)
        int fetchLimit = Math.min(deduplicatedCandidates.size(), 35);
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

        // 5. Final deduplication pass to ensure clean output
        List<RawArticle> finalPool = deduplicationService.deduplicate(fetchedArticles);
        log.info("Phase 3 Research Pipeline completed. Final pool size: {} articles for topic '{}'", finalPool.size(), topicQuery);

        return finalPool;
    }

    private List<String> generateSubQueries(String topic) {
        String trimmed = topic.trim();
        List<String> queries = new ArrayList<>();
        // Keep the base topic unchanged so search engines can apply their own relevance ranking.
        queries.add(trimmed);
        // Broaden with a generic "news" qualifier instead of strict temporal strings.
        // Strict qualifiers like " this week" / " latest news" cause the search API to
        // return 0 results; the temporal filter is already enforced downstream by the
        // Java Freshness Filter (digest.freshness.max-age-hours).
        queries.add(trimmed + " news");
        // Anchor one query to the current year so we still bias toward recent content
        // without forcing a strict temporal phrase that breaks the search backend.
        String currentYear = String.valueOf(Year.now().getValue());
        queries.add(trimmed + " " + currentYear);
        return queries;
    }
}
