package com.beat.service;

import com.beat.dto.RawArticle;
import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.repository.DigestRunRepository;
import com.beat.repository.NewsItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DigestPipelineServiceTest {

    @Mock
    private ResearchPipelineService researchPipelineService;

    @Mock
    private LlmDigestService llmDigestService;

    @Mock
    private DigestRunRepository digestRunRepository;

    @Mock
    private NewsItemRepository newsItemRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private GroqUsageTracker groqUsageTracker;

    @InjectMocks
    private DigestPipelineService digestPipelineService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(digestPipelineService, "maxAgeHours", 168);
    }

    @Test
    void executeDigestPipeline_filtersStaleArticles() throws Exception {
        Channel channel = new Channel();
        channel.setId(1L);
        channel.setName("Test Channel");
        channel.setTopicQuery("test");
        channel.setArticleCount(5);

        DigestRun mockRun = new DigestRun();
        mockRun.setId(100L);
        when(digestRunRepository.save(any(DigestRun.class))).thenReturn(mockRun);

        RawArticle freshArticle = new RawArticle();
        freshArticle.setTitle("Fresh");
        freshArticle.setPublishedAt(Instant.now().minus(24, ChronoUnit.HOURS).toString()); // 1 day old

        RawArticle staleArticle = new RawArticle();
        staleArticle.setTitle("Stale");
        staleArticle.setPublishedAt(Instant.now().minus(200, ChronoUnit.HOURS).toString()); // > 168 hours old

        RawArticle nullDateArticle = new RawArticle();
        nullDateArticle.setTitle("Null Date");
        nullDateArticle.setPublishedAt(null);

        List<RawArticle> candidates = Arrays.asList(freshArticle, staleArticle, nullDateArticle);
        when(researchPipelineService.executeResearch("test")).thenReturn(candidates);

        when(llmDigestService.clusterAndRank(anyList(), eq("test"), eq(5))).thenReturn(List.of(freshArticle));

        digestPipelineService.executeDigestPipeline(channel);

        // Verify clusterAndRank was called with only the fresh article
        verify(llmDigestService).clusterAndRank(argThat(list -> 
            list.size() == 1 && list.get(0).getTitle().equals("Fresh")
        ), eq("test"), eq(5));
    }
}
