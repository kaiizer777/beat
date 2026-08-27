package com.beat.service;

import com.beat.dto.RawArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ResearchPipelineServiceTest {

    @Mock
    private TinyFishClient tinyFishClient;

    @Mock
    private TinyFishFetchClient fetchClient;

    @Mock
    private ArticleDeduplicationService deduplicationService;

    @InjectMocks
    private ResearchPipelineService researchPipelineService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(researchPipelineService, "defaultMaxAgeHours", 168);
        ReflectionTestUtils.setField(researchPipelineService, "blockedDomains",
                java.util.List.of("slideshare.net", "pinterest.com", "quora.com", "facebook.com"));
    }

    @Test
    void executeResearch_filtersStaleArticlesAndSortsByDate() {
        // Prepare mock responses
        TinyFishClient.SearchResultItem freshItem = new TinyFishClient.SearchResultItem("Fresh", "http://fresh.com", "snippet", "publisher", Instant.now().minus(24, ChronoUnit.HOURS).toString());

        TinyFishClient.SearchResultItem staleItem = new TinyFishClient.SearchResultItem("Stale", "http://stale.com", "snippet", "publisher", Instant.now().minus(200, ChronoUnit.HOURS).toString());

        TinyFishClient.SearchResultItem nullDateItem = new TinyFishClient.SearchResultItem("Null Date", "http://nulldate.com", "snippet", "publisher", null);
        
        TinyFishClient.SearchResultItem olderFreshItem = new TinyFishClient.SearchResultItem("Older Fresh", "http://olderfresh.com", "snippet", "publisher", Instant.now().minus(48, ChronoUnit.HOURS).toString());

        // Only return on the first sub-query to keep it simple, return empty for others
        when(tinyFishClient.searchNews(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.equals("test")) {
                return List.of(staleItem, olderFreshItem, freshItem, nullDateItem);
            }
            return List.of();
        });

        // Mock deduplication to just pass through
        when(deduplicationService.deduplicate(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock fetch client to succeed
        TinyFishFetchClient.FetchResult fetchResult = new TinyFishFetchClient.FetchResult("Full text content", "tinyfish");
        when(fetchClient.fetchContent(anyString())).thenReturn(fetchResult);

        // Execute: pass freshness window 168h and targetCount=0 (no broader-search fallback)
        List<RawArticle> finalPool = researchPipelineService
                .executeResearch("test", 168, 0).getArticles();

        // Verify
        // Stale item (200 hours old) should be filtered out.
        // E2: Null date item is now KEPT (TinyFish news API implies recency).
        // Remaining should be "Fresh", "Older Fresh", and "Null Date".
        // Sorted newest first: "Fresh" (1 day old), "Older Fresh" (2 days old), "Null Date" (nulls-last).
        assertEquals(3, finalPool.size());
        assertEquals("Fresh", finalPool.get(0).getTitle());
        assertEquals("Older Fresh", finalPool.get(1).getTitle());
        assertEquals("Null Date", finalPool.get(2).getTitle());
    }

    @Test
    void cleanTopic_sanitizesNumberRangesAndNoisePatterns() {
        assertEquals("ai model", researchPipelineService.cleanTopic("latest ai model news past 2-3 days"));
        assertEquals("ai model", researchPipelineService.cleanTopic("latest ai model news past 24-48 hours"));
        assertEquals("ai model", researchPipelineService.cleanTopic("ai model 48h"));
        assertEquals("quantum computing", researchPipelineService.cleanTopic("quantum computing 7d news"));
        assertEquals("crypto developments", researchPipelineService.cleanTopic("top 10 crypto developments this week"));
        assertEquals("electric vehicles", researchPipelineService.cleanTopic("breaking electric vehicles news"));
        assertEquals("gpt-4 vision", researchPipelineService.cleanTopic("gpt-4 vision latest updates"));
    }

    @Test
    void generateSubQueries_producesFiveOrthogonalIntents() {
        List<String> subQueries = researchPipelineService.generateSubQueries("quantum computing");
        assertEquals(5, subQueries.size());
        assertEquals("quantum computing", subQueries.get(0));
        assertEquals("quantum computing latest news", subQueries.get(1));
        assertEquals("quantum computing research paper", subQueries.get(2));
        assertEquals("quantum computing market analysis", subQueries.get(3));
        assertEquals("quantum computing technical deep dive", subQueries.get(4));
    }

    @Test
    void executeResearch_expandsFreshnessWindowTo30DaysOnStarvation() {
        TinyFishClient.SearchResultItem freshItem = new TinyFishClient.SearchResultItem("Fresh", "http://fresh.com", "snippet", "publisher", Instant.now().minus(24, ChronoUnit.HOURS).toString());
        // 200 hours old: would be dropped by 168h window, but kept by expanded 30-day (720h) window!
        TinyFishClient.SearchResultItem olderItem = new TinyFishClient.SearchResultItem("200h Old", "http://old200.com", "snippet", "publisher", Instant.now().minus(200, ChronoUnit.HOURS).toString());
        TinyFishClient.SearchResultItem nullDateItem = new TinyFishClient.SearchResultItem("Null Date", "http://nulldate.com", "snippet", "publisher", null);
        TinyFishClient.SearchResultItem freshItem2 = new TinyFishClient.SearchResultItem("Fresh 2", "http://fresh2.com", "snippet", "publisher", Instant.now().minus(48, ChronoUnit.HOURS).toString());

        when(tinyFishClient.searchNews(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.equals("ai")) {
                return List.of(freshItem, olderItem, nullDateItem, freshItem2);
            }
            return List.of();
        });
        when(deduplicationService.deduplicate(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchClient.fetchContent(anyString())).thenReturn(new TinyFishFetchClient.FetchResult("Full content text", "tinyfish"));

        // targetCount = 15 -> starvation threshold is 30. candidates = 4 < 30 -> expands window to 30 days (720h)
        List<RawArticle> pool = researchPipelineService.executeResearch("ai", 168, 15).getArticles();

        // All 4 should be kept because 200h is within 720h
        assertEquals(4, pool.size());
        assertTrue(pool.stream().anyMatch(a -> a.getTitle().equals("200h Old")));
    }

    @Test
    void executeResearch_nullDateSortedNullsLastBehindDatedArticles() {
        TinyFishClient.SearchResultItem newest = new TinyFishClient.SearchResultItem("Newest 1d", "http://1d.com", "snippet", "publisher", Instant.now().minus(24, ChronoUnit.HOURS).toString());
        TinyFishClient.SearchResultItem older = new TinyFishClient.SearchResultItem("Older 20d", "http://20d.com", "snippet", "publisher", Instant.now().minus(480, ChronoUnit.HOURS).toString());
        TinyFishClient.SearchResultItem nullDate = new TinyFishClient.SearchResultItem("Null Date", "http://null.com", "meaningful snippet text", "publisher", null);

        when(tinyFishClient.searchNews(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.equals("tech")) {
                return List.of(older, newest, nullDate);
            }
            return List.of();
        });
        when(deduplicationService.deduplicate(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchClient.fetchContent(anyString())).thenReturn(new TinyFishFetchClient.FetchResult("Full content text", "tinyfish"));

        // targetCount = 15, starvation expands window to 30d (720h).
        // Order should be: Newest 1d (24h) -> Older 20d (480h) -> Null Date (nulls-last).
        List<RawArticle> pool = researchPipelineService.executeResearch("tech", 168, 15).getArticles();

        assertEquals(3, pool.size());
        assertEquals("Newest 1d", pool.get(0).getTitle());
        assertEquals("Older 20d", pool.get(1).getTitle());
        assertEquals("Null Date", pool.get(2).getTitle());
    }

    @Test
    void executeResearch_starvationTriggeredAfterFreshnessFilterWhenRawCandidatesExceedThreshold() {
        // 35 raw candidates: under previous code, 35 >= target*2 (30), so starvation window never expanded!
        // But 34 of them are 20 days old (480h), and only 1 is fresh within 7 days (24h).
        // With starvation check AFTER freshness filter, freshCandidates.size() (1) < target*2 (30),
        // so window expands to 30d (720h) and re-filters to include all 35 candidates!
        TinyFishClient.SearchResultItem fresh = new TinyFishClient.SearchResultItem("Fresh 1d", "http://fresh.com", "snippet", "publisher", Instant.now().minus(24, ChronoUnit.HOURS).toString());
        java.util.List<TinyFishClient.SearchResultItem> items = new java.util.ArrayList<>();
        items.add(fresh);
        for (int i = 1; i <= 34; i++) {
            items.add(new TinyFishClient.SearchResultItem("Older " + i, "http://older" + i + ".com", "snippet", "publisher", Instant.now().minus(480, ChronoUnit.HOURS).toString()));
        }

        when(tinyFishClient.searchNews(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.equals("niche ai")) {
                return items;
            }
            return List.of();
        });
        when(deduplicationService.deduplicate(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchClient.fetchContent(anyString())).thenReturn(new TinyFishFetchClient.FetchResult("Full content text", "tinyfish"));

        List<RawArticle> pool = researchPipelineService.executeResearch("niche ai", 168, 15).getArticles();

        assertEquals(35, pool.size());
    }

    @Test
    void executeResearch_yearlessDatesParsedAndSortedNewestFirstAheadOfNullDates() {
        TinyFishClient.SearchResultItem aug28 = new TinyFishClient.SearchResultItem("Aug 28 Article", "http://aug28.com", "snippet", "publisher", "2026-08-28T00:00:00Z");
        TinyFishClient.SearchResultItem aug25 = new TinyFishClient.SearchResultItem("Aug 25 Article", "http://aug25.com", "snippet", "publisher", "Aug 25");
        TinyFishClient.SearchResultItem nullDate = new TinyFishClient.SearchResultItem("Null Date Article", "http://null.com", "snippet", "publisher", null);

        when(tinyFishClient.searchNews(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.equals("recency test")) {
                return List.of(aug25, nullDate, aug28);
            }
            return List.of();
        });
        when(deduplicationService.deduplicate(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchClient.fetchContent(anyString())).thenReturn(new TinyFishFetchClient.FetchResult("Full content text", "tinyfish"));

        List<RawArticle> pool = researchPipelineService.executeResearch("recency test", 168, 0).getArticles();

        assertEquals(3, pool.size());
        assertEquals("Aug 28 Article", pool.get(0).getTitle());
        assertEquals("Aug 25 Article", pool.get(1).getTitle());
        assertEquals("Null Date Article", pool.get(2).getTitle());
    }

    @Test
    void executeResearch_aiCodingAgentNews_starvationWindowExpandsAndTopSortedByAug28First() {
        List<TinyFishClient.SearchResultItem> items = new java.util.ArrayList<>();
        items.add(new TinyFishClient.SearchResultItem("Aug 28 Story 1", "http://aug28-1.com", "snippet", "TechCrunch", "2026-08-28T01:00:00Z"));
        items.add(new TinyFishClient.SearchResultItem("Aug 28 Story 2", "http://aug28-2.com", "snippet", "VentureBeat", "Aug 28"));
        items.add(new TinyFishClient.SearchResultItem("Aug 27 Story 1", "http://aug27-1.com", "snippet", "Reuters", "2026-08-27T10:00:00Z"));
        items.add(new TinyFishClient.SearchResultItem("Aug 25 Story 1", "http://aug25-1.com", "snippet", "Wired", "Aug 25"));

        for (int i = 1; i <= 10; i++) {
            items.add(new TinyFishClient.SearchResultItem("Older Day " + (10 + i), "http://older" + i + ".com", "snippet", "Pub",
                    Instant.now().minus(240 + i * 24, ChronoUnit.HOURS).toString()));
        }
        items.add(new TinyFishClient.SearchResultItem("Null Date Story", "http://nullstory.com", "snippet", "Pub", null));

        when(tinyFishClient.searchNews(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.equals("ai coding agent news")) {
                return items;
            }
            return List.of();
        });
        when(deduplicationService.deduplicate(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchClient.fetchContent(anyString())).thenReturn(new TinyFishFetchClient.FetchResult("Full content text", "tinyfish"));

        ResearchResult result = researchPipelineService.executeResearch("ai coding agent news", 168, 15);
        List<RawArticle> pool = result.getArticles();
        java.util.Map<String, Object> metrics = result.getMetrics();

        int freshKept = (Integer) metrics.get("freshnessKept");
        assertTrue(freshKept >= 12, "fresh after 30d must be 12+, was: " + freshKept);

        assertEquals("Aug 28 Story 1", pool.get(0).getTitle());
        assertEquals("Aug 28 Story 2", pool.get(1).getTitle());
        assertEquals("Aug 27 Story 1", pool.get(2).getTitle());
        assertEquals("Aug 25 Story 1", pool.get(3).getTitle());
        assertEquals("Null Date Story", pool.get(pool.size() - 1).getTitle());
    }
}
