package com.beat.service;

import com.beat.dto.RawArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /**
     * Default freshness window in hours, used as a fallback when a channel does not
     * specify {@code freshnessWindowDays}. Configurable via
     * {@code digest.freshness.max-age-hours} in application.yml.
     */
    @Value("${digest.freshness.max-age-hours:168}")
    private int defaultMaxAgeHours;

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

    /**
     * Run the research pipeline.
     *
     * @param topicQuery   the user's search topic
     * @param maxAgeHours  freshness cutoff in hours (caller computes from channel.freshnessWindowDays)
     * @param targetCount  the digest target. Used to decide whether to run a broader-search fallback
     *                     when the first pass lands below the target. Pass 0 to disable the fallback.
     */
    public ResearchResult executeResearch(String topicQuery, int maxAgeHours, int targetCount) {
        if (topicQuery == null || topicQuery.isBlank()) {
            log.warn("Empty topicQuery received for research pipeline");
            return new ResearchResult(List.of(), Map.of("error", "empty topicQuery"));
        }

        // Use LinkedHashMap to preserve insertion order in the final JSON-style log line.
        Map<String, Object> metrics = new LinkedHashMap<>();

        log.info("Starting research pipeline for topic: '{}' (maxAgeHours={}, targetCount={})",
                topicQuery, maxAgeHours, targetCount);

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
        metrics.put("finalPoolSize", finalPool.size());
        metrics.put("finalDedupDropped", beforeFinalDedup - finalPool.size());

        // 6. Broader-search fallback. If the first pass landed below the target
        // (typical for narrow topics with a 7-day window where the world simply
        // doesn't have enough fresh stories), run a second pass with simpler
        // queries and merge into the pool. Triggered when finalPool.size() is
        // strictly less than targetCount; only runs if targetCount > 0.
        if (targetCount > 0 && finalPool.size() < targetCount) {
            log.info("Final pool ({}) < targetCount ({}). Running broader-search fallback.",
                    finalPool.size(), targetCount);
            List<String> broaderQueries = generateBroaderQueries(topicQuery);
            log.info("Broader queries: {}", broaderQueries);

            // Run the same search→freshness→dedup→fetch→dedup cycle, but on the
            // broader query set. Reuse the same maxAgeHours (the freshness window
            // is per-channel, not per-pass). The same 1000ms / 300ms throttles apply.
            List<RawArticle> broaderPool = runSearchPass(broaderQueries, maxAgeHours, metrics, "broader");

            // Merge: existing pool first (preserves cluster/rank ordering), then broader.
            // The dedup on the merged list normalizes URL/title and keeps first occurrence.
            int beforeMergedSize = finalPool.size();
            List<RawArticle> merged = new ArrayList<>(finalPool);
            merged.addAll(broaderPool);
            int beforeMergeDedup = merged.size();
            finalPool = deduplicationService.deduplicate(merged);
            int addedFromBroader = finalPool.size() - beforeMergedSize;
            int mergeDupDropped = beforeMergeDedup - finalPool.size();
            log.info("Broader fallback merged: kept {}/{} new articles from broader search (dropped {} as duplicates)",
                    addedFromBroader, broaderPool.size(), mergeDupDropped);
            metrics.put("broaderSearchTriggered", true);
            metrics.put("broaderQueries", broaderQueries);
            metrics.put("broaderRawKept", broaderPool.size());
            metrics.put("broaderAddedToPool", addedFromBroader);
            metrics.put("broaderMergeDuplicatesDropped", mergeDupDropped);
        } else {
            metrics.put("broaderSearchTriggered", false);
        }

        log.info("Research Pipeline completed. Final pool size: {} articles for topic '{}'",
                finalPool.size(), topicQuery);
        metrics.put("finalPoolSize", finalPool.size());

        return new ResearchResult(finalPool, metrics);
    }

    /**
     * Generate a broader set of sub-queries for the fallback pass. The aim is to
     * surface articles that the more specific 5 sub-queries may have missed —
     * typically because the topic is multi-word and the truncated terms match
     * a different, broader news corpus. We never include the same query twice.
     *
     * Examples:
     *   "coding related ai"  -> ["coding related ai", "coding related", "coding"]
     *   "AI"                 -> ["AI"]                          (no fallback possible)
     *   "machine learning"   -> ["machine learning", "machine", "learning"]
     */
    private List<String> generateBroaderQueries(String topic) {
        String trimmed = topic.trim();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(trimmed);
        String[] words = trimmed.split("\\s+");
        if (words.length >= 3) {
            // Take the first two words
            String firstTwo = words[0] + " " + words[1];
            if (!firstTwo.equals(trimmed)) {
                out.add(firstTwo);
            }
        }
        if (words.length >= 2) {
            // First single word, if it's long enough to be a meaningful topic
            if (words[0].length() >= 3) {
                out.add(words[0]);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Run a single pass of the search pipeline (search + freshness + sort + dedup +
     * fetch + final dedup) for a given list of sub-queries. Used by both the main
     * pass and the broader-search fallback. Metrics are written under
     * {@code metrics[passName + "_" + key]} so the two passes' numbers stay
     * separable in the final PIPELINE_METRICS log.
     */
    private List<RawArticle> runSearchPass(List<String> subQueries, int maxAgeHours,
                                           Map<String, Object> metrics, String passName) {
        Map<String, Integer> perSubQuery = new LinkedHashMap<>();
        List<RawArticle> rawList = new ArrayList<>();
        for (String q : subQueries) {
            List<TinyFishClient.SearchResultItem> items = tinyFishClient.searchNews(q);
            perSubQuery.put(q, items.size());
            for (TinyFishClient.SearchResultItem item : items) {
                rawList.add(new RawArticle(
                        item.getTitle(), item.getUrl(), item.getSnippet(),
                        item.getPublisher(), item.getDate(), null, null));
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        metrics.put(passName + "_subQueryCount", subQueries.size());
        metrics.put(passName + "_perSubQueryCounts", perSubQuery);
        metrics.put(passName + "_rawCandidates", rawList.size());

        // Freshness filter
        Instant cutoff = Instant.now().minus(maxAgeHours, ChronoUnit.HOURS);
        int initialSize = rawList.size();
        int droppedNullOrUnparseable = 0;
        int droppedStale = 0;
        for (RawArticle a : rawList) {
            Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
            if (pub == null) {
                droppedNullOrUnparseable++;
            } else if (!pub.isAfter(cutoff)) {
                droppedStale++;
            }
        }
        rawList = rawList.stream()
                .filter(a -> {
                    Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
                    return pub != null && pub.isAfter(cutoff);
                })
                .collect(Collectors.toList());
        metrics.put(passName + "_freshnessKept", rawList.size());
        metrics.put(passName + "_freshnessDroppedTotal", initialSize - rawList.size());

        // Sort newest-first (consistency with main pass)
        rawList.sort(Comparator.comparing(
                (RawArticle a) -> com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt()),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        // Pre-fetch dedup
        int preDedup = rawList.size();
        List<RawArticle> deduped = deduplicationService.deduplicate(rawList);
        metrics.put(passName + "_afterPreFetchDedup", deduped.size());
        metrics.put(passName + "_preFetchDedupDropped", preDedup - deduped.size());

        // Fetch with cap of 50
        int fetchLimit = Math.min(deduped.size(), 50);
        List<RawArticle> fetched = new ArrayList<>();
        int tinyFish = 0, jina = 0, failed = 0;
        for (int i = 0; i < fetchLimit; i++) {
            RawArticle c = deduped.get(i);
            TinyFishFetchClient.FetchResult fr = fetchClient.fetchContent(c.getUrl());
            if (fr != null && fr.getContent() != null && !fr.getContent().isBlank()) {
                c.setFullText(fr.getContent());
                c.setFetchSource(fr.getSource());
                fetched.add(c);
                if ("tinyfish".equalsIgnoreCase(fr.getSource())) tinyFish++;
                else jina++;
            } else {
                failed++;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        metrics.put(passName + "_fetchAttempted", fetchLimit);
        metrics.put(passName + "_fetchSucceeded", tinyFish + jina);
        metrics.put(passName + "_fetchTinyfishSuccess", tinyFish);
        metrics.put(passName + "_fetchJinaSuccess", jina);
        metrics.put(passName + "_fetchFailed", failed);

        // Final dedup
        int beforeFinalDedup = fetched.size();
        List<RawArticle> finalPool = deduplicationService.deduplicate(fetched);
        metrics.put(passName + "_finalPoolSize", finalPool.size());
        metrics.put(passName + "_finalDedupDropped", beforeFinalDedup - finalPool.size());
        return finalPool;
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
