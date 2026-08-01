package com.beat.service;

import com.beat.dto.RawArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ResearchPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipelineService.class);

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

        // 3. Early deduplication pass on raw candidates before fetching full text
        List<RawArticle> deduplicatedCandidates = deduplicationService.deduplicate(rawCandidateList);
        log.info("Candidate pool size after initial deduplication: {}", deduplicatedCandidates.size());

        // Limit maximum candidate fetches per run to prevent excessive overhead (e.g. max 35 articles)
        int fetchLimit = Math.min(deduplicatedCandidates.size(), 35);
        List<RawArticle> fetchedArticles = new ArrayList<>();

        // 4. Fetch full text for candidates using TinyFish Fetch + Jina fallback
        for (int i = 0; i < fetchLimit; i++) {
            RawArticle candidate = deduplicatedCandidates.get(i);
            log.info("Fetching full text ({}/{}): {}", i + 1, fetchLimit, candidate.getUrl());

            TinyFishFetchClient.FetchResult fetchResult = fetchClient.fetchContent(candidate.getUrl());
            if (fetchResult != null && fetchResult.getContent() != null && !fetchResult.getContent().isBlank()) {
                candidate.setFullText(fetchResult.getContent());
                candidate.setFetchSource(fetchResult.getSource());
                fetchedArticles.add(candidate);
            } else {
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

        log.info("Successfully fetched full text for {} articles", fetchedArticles.size());

        // 5. Final deduplication pass to ensure clean output
        List<RawArticle> finalPool = deduplicationService.deduplicate(fetchedArticles);
        log.info("Phase 3 Research Pipeline completed. Final pool size: {} articles for topic '{}'", finalPool.size(), topicQuery);

        return finalPool;
    }

    private List<String> generateSubQueries(String topic) {
        String trimmed = topic.trim();
        List<String> queries = new ArrayList<>();
        queries.add(trimmed);
        queries.add(trimmed + " latest news");
        queries.add(trimmed + " this week");
        queries.add(trimmed + " analysis");
        return queries;
    }
}
