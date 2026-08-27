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

    // E5: Configurable domain blocklist for noise/spam/paywall sources
    @Value("${digest.source.blocked-domains:slideshare.net,pinterest.com,quora.com,facebook.com}")
    private List<String> blockedDomains;

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

        // E5: Strip blocked-domain articles before any further processing
        int beforeBlocklist = rawCandidateList.size();
        rawCandidateList.removeIf(a -> isBlockedDomain(a.getUrl()));
        int blocklistDropped = beforeBlocklist - rawCandidateList.size();
        if (blocklistDropped > 0) {
            log.info("Domain blocklist: dropped {} articles from blocked sources", blocklistDropped);
        }
        metrics.put("blocklistDropped", blocklistDropped);

        // Early deduplication pass on raw candidates before freshness filter
        int preDedupSize = rawCandidateList.size();
        List<RawArticle> deduplicatedCandidates = deduplicationService.deduplicate(rawCandidateList);
        log.info("Candidate pool size after initial deduplication: {} (dropped {})",
                deduplicatedCandidates.size(), preDedupSize - deduplicatedCandidates.size());
        metrics.put("afterPreFetchDedup", deduplicatedCandidates.size());
        metrics.put("preFetchDedupDropped", preDedupSize - deduplicatedCandidates.size());

        // Freshness Dynamic Window: expand window if candidate pool is starving (< targetCount * 2)
        int candidateCount = deduplicatedCandidates.size();
        int freshnessWindowDays = maxAgeHours > 0 ? (maxAgeHours / 24) : 7;
        int dynamicWindowDays = freshnessWindowDays;
        if (targetCount > 0 && candidateCount < targetCount * 2) {
            dynamicWindowDays = Math.max(freshnessWindowDays, 14);
            log.info("Expanding freshness window to {}d due to starvation (candidates={} < {})",
                    dynamicWindowDays, candidateCount, targetCount * 2);
        }
        int effectiveMaxAgeHours = dynamicWindowDays * 24;
        Instant cutoff = Instant.now().minus(effectiveMaxAgeHours, ChronoUnit.HOURS);

        int initialFreshnessSize = deduplicatedCandidates.size();
        int droppedNullOrUnparseable = 0;
        int droppedStale = 0;
        List<RawArticle> freshCandidates = new ArrayList<>();
        for (RawArticle a : deduplicatedCandidates) {
            Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
            if (pub == null) {
                droppedNullOrUnparseable++;
                freshCandidates.add(a);
            } else if (pub.isAfter(cutoff)) {
                freshCandidates.add(a);
            } else {
                droppedStale++;
            }
        }
        int dropped = initialFreshnessSize - freshCandidates.size();
        log.info("Freshness filter: kept {} / dropped {} (nullKept={}, staleDropped={})",
                freshCandidates.size(), dropped, droppedNullOrUnparseable, droppedStale);
        metrics.put("freshnessKept", freshCandidates.size());
        metrics.put("freshnessDroppedTotal", dropped);
        metrics.put("freshnessKeptNullDate", droppedNullOrUnparseable);
        metrics.put("freshnessDroppedStale", droppedStale);

        // Sort: newest first, null-date intermixed mid-list if content heuristic passes
        freshCandidates.sort(Comparator.comparing(
                (RawArticle a) -> resolveEffectiveDate(a, effectiveMaxAgeHours),
                Comparator.nullsLast(Comparator.reverseOrder())
        ).thenComparing(Comparator.comparingInt(this::getContentLength).reversed()));

        deduplicatedCandidates = freshCandidates;

        // F2: Dynamic fetch cap — 4x headroom, min 30
        int fetchLimit = Math.min(deduplicatedCandidates.size(), Math.max(targetCount * 4, 30));
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
            List<String> broaderQueries = generateBroaderQueries(topicQuery, subQueries);
            log.info("Broader queries: {}", broaderQueries);

            // Run the same search→freshness→dedup→fetch→dedup cycle, but on the
            // broader query set. Reuse the same maxAgeHours (the freshness window
            // is per-channel, not per-pass). The same 1000ms / 300ms throttles apply.
            List<RawArticle> broaderPool = runSearchPass(broaderQueries, maxAgeHours, targetCount, metrics, "broader");

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

    private static final java.util.Set<String> NOISE_WORDS = java.util.Set.of(
            "past", "day", "days", "hour", "hours", "week", "weeks", "month", "months", "year", "years",
            "today", "yesterday", "latest", "top", "recent", "news", "daily", "breaking", "new", "update",
            "updates", "best", "the", "in", "for", "on", "of", "and", "to", "a", "an", "this", "about"
    );

    private static final java.util.regex.Pattern NUMBER_RANGE_OR_TIME_PATTERN = java.util.regex.Pattern.compile(
            "^(\\d+([-\\/]\\d+)?|\\d+to\\d+)(h|d|w|m|y|hr|hrs|day|days|wk|wks|week|weeks|mo|mos|month|months|yr|yrs|year|years|min|mins)?$",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    String cleanTopic(String topic) {
        if (topic == null || topic.isBlank()) return "";
        String[] tokens = topic.trim().toLowerCase().split("\\s+");
        List<String> meaningful = new ArrayList<>();
        for (String token : tokens) {
            String cleaned = token.replaceAll("[^a-zA-Z0-9-]", "");
            if (!cleaned.isBlank()
                    && !NOISE_WORDS.contains(cleaned)
                    && !NUMBER_RANGE_OR_TIME_PATTERN.matcher(cleaned).matches()) {
                meaningful.add(cleaned);
            }
        }
        return meaningful.isEmpty() ? topic.trim() : String.join(" ", meaningful);
    }

    // E5: Check if a URL belongs to a blocked domain
    private boolean isBlockedDomain(String url) {
        if (url == null || url.isBlank() || blockedDomains == null || blockedDomains.isEmpty()) {
            return false;
        }
        String normalized = deduplicationService != null ? deduplicationService.normalizeUrl(url) : null;
        if (normalized == null || normalized.isBlank()) {
            normalized = url;
        }
        String lowerNormalized = normalized.toLowerCase();
        return blockedDomains.stream().anyMatch(domain -> lowerNormalized.contains(domain.trim().toLowerCase()));
    }

    /**
     * Generate a broader set of sub-queries for the fallback pass.
     * F3: Guard against generating a query identical to any initial sub-query.
     */
    private List<String> generateBroaderQueries(String topic, List<String> initialSubQueries) {
        String cleaned = cleanTopic(topic);
        String baseTopic = !cleaned.isBlank() ? cleaned : topic.trim();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String[] words = baseTopic.split("\\s+");

        if (words.length >= 3) {
            String firstTwo = words[0] + " " + words[1];
            if (!firstTwo.equalsIgnoreCase(baseTopic)) {
                out.add(firstTwo);
            }
        }
        if (words.length >= 2) {
            if (words[0].length() >= 2 && !NOISE_WORDS.contains(words[0].toLowerCase())) {
                out.add(words[0]);
            }
        }
        // Fallback: add baseTopic only if it wasn't already an initial sub-query
        if (out.isEmpty()) {
            out.add(baseTopic);
        }

        // F3: Remove any broader query that duplicates an initial sub-query
        if (initialSubQueries != null) {
            out.removeIf(q -> initialSubQueries.stream().anyMatch(q::equalsIgnoreCase));
        }

        // If everything was filtered out, keep at least one fallback
        if (out.isEmpty()) {
            out.add(baseTopic + " latest");
        }

        return new ArrayList<>(out);
    }

    /**
     * Run a single pass of the search pipeline (search + blocklist + freshness + sort + dedup +
     * fetch + final dedup) for a given list of sub-queries.
     */
    private List<RawArticle> runSearchPass(List<String> subQueries, int maxAgeHours, int targetCount,
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

        // E5: Blocklist filter
        rawList.removeIf(a -> isBlockedDomain(a.getUrl()));

        // Pre-fetch dedup
        int preDedup = rawList.size();
        List<RawArticle> deduped = deduplicationService.deduplicate(rawList);
        metrics.put(passName + "_afterPreFetchDedup", deduped.size());
        metrics.put(passName + "_preFetchDedupDropped", preDedup - deduped.size());

        // Dynamic freshness window
        int candidateCount = deduped.size();
        int freshnessWindowDays = maxAgeHours > 0 ? (maxAgeHours / 24) : 7;
        int dynamicWindowDays = freshnessWindowDays;
        if (targetCount > 0 && candidateCount < targetCount * 2) {
            dynamicWindowDays = Math.max(freshnessWindowDays, 14);
            log.info("Expanding freshness window to {}d due to starvation in {} pass (candidates={} < {})",
                    dynamicWindowDays, passName, candidateCount, targetCount * 2);
        }
        int effectiveMaxAgeHours = dynamicWindowDays * 24;
        Instant cutoff = Instant.now().minus(effectiveMaxAgeHours, ChronoUnit.HOURS);

        int beforeFreshness = deduped.size();
        List<RawArticle> freshDeduped = new ArrayList<>();
        for (RawArticle a : deduped) {
            Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
            if (pub == null || pub.isAfter(cutoff)) {
                freshDeduped.add(a);
            }
        }
        metrics.put(passName + "_freshnessKept", freshDeduped.size());
        metrics.put(passName + "_freshnessDroppedTotal", beforeFreshness - freshDeduped.size());

        // Sort newest-first, intermix null-date mid-list if content heuristic passes
        freshDeduped.sort(Comparator.comparing(
                (RawArticle a) -> resolveEffectiveDate(a, effectiveMaxAgeHours),
                Comparator.nullsLast(Comparator.reverseOrder())
        ).thenComparing(Comparator.comparingInt(this::getContentLength).reversed()));

        // F2: Dynamic fetch cap — 4x headroom, min 30
        int fetchLimit = Math.min(freshDeduped.size(), Math.max(targetCount * 4, 30));
        List<RawArticle> fetched = new ArrayList<>();
        int tinyFish = 0, jina = 0, failed = 0;
        for (int i = 0; i < fetchLimit; i++) {
            RawArticle c = freshDeduped.get(i);
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

    /**
     * Topic-derived orthogonal sub-queries across 5 distinct intents:
     * [topic, topic+" latest news", topic+" research paper", topic+" market analysis", topic+" technical deep dive"]
     */
    List<String> generateSubQueries(String topic) {
        if (topic == null || topic.isBlank()) {
            return List.of();
        }
        String trimmed = topic.trim();
        List<String> queries = new ArrayList<>(5);
        queries.add(trimmed);
        queries.add(trimmed + " latest news");
        queries.add(trimmed + " research paper");
        queries.add(trimmed + " market analysis");
        queries.add(trimmed + " technical deep dive");
        return queries;
    }

    private Instant resolveEffectiveDate(RawArticle a, int effectiveMaxAgeHours) {
        if (a == null) return null;
        Instant pub = com.beat.util.DateParserUtils.parseInstantOrNull(a.getPublishedAt());
        if (pub != null) {
            return pub;
        }
        if (hasContentLengthHeuristic(a)) {
            return Instant.now().minus(effectiveMaxAgeHours / 2, ChronoUnit.HOURS);
        }
        return null;
    }

    private boolean hasContentLengthHeuristic(RawArticle a) {
        if (a == null) return false;
        if (a.getFullText() != null && a.getFullText().length() > 500) {
            return true;
        }
        return a.getSnippet() != null && !a.getSnippet().isBlank();
    }

    private int getContentLength(RawArticle a) {
        if (a == null) return 0;
        if (a.getFullText() != null) return a.getFullText().length();
        if (a.getSnippet() != null) return a.getSnippet().length();
        return 0;
    }
}
