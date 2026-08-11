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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        ReflectionTestUtils.setField(researchPipelineService, "maxAgeHours", 168);
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

        // Execute
        List<RawArticle> finalPool = researchPipelineService.executeResearch("test");

        // Verify
        // Stale item (200 hours old) should be filtered out.
        // Null date item should be filtered out (per freshness filter logic pub != null).
        // Remaining should be "Fresh" and "Older Fresh".
        // They should be sorted newest first, so "Fresh" (1 day old) before "Older Fresh" (2 days old).
        assertEquals(2, finalPool.size());
        assertEquals("Fresh", finalPool.get(0).getTitle());
        assertEquals("Older Fresh", finalPool.get(1).getTitle());
    }
}
