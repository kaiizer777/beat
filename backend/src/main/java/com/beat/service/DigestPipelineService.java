package com.beat.service;

import com.beat.dto.RawArticle;
import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.entity.DigestRunStatus;
import com.beat.entity.NewsItem;
import com.beat.repository.DigestRunRepository;
import com.beat.repository.NewsItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DigestPipelineService {

    private static final Logger log = LoggerFactory.getLogger(DigestPipelineService.class);

    private final ResearchPipelineService researchPipelineService;
    private final LlmDigestService llmDigestService;
    private final DigestRunRepository digestRunRepository;
    private final NewsItemRepository newsItemRepository;
    private final EmailService emailService;
    private final GroqUsageTracker groqUsageTracker;

    public DigestPipelineService(ResearchPipelineService researchPipelineService,
                                  LlmDigestService llmDigestService,
                                  DigestRunRepository digestRunRepository,
                                  NewsItemRepository newsItemRepository,
                                  EmailService emailService,
                                  GroqUsageTracker groqUsageTracker) {
        this.researchPipelineService = researchPipelineService;
        this.llmDigestService = llmDigestService;
        this.digestRunRepository = digestRunRepository;
        this.newsItemRepository = newsItemRepository;
        this.emailService = emailService;
        this.groqUsageTracker = groqUsageTracker;
    }

    public DigestRun executeDigestPipeline(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }

        long startTime = System.currentTimeMillis();
        DigestRun digestRun = new DigestRun(channel, Instant.now(), DigestRunStatus.PENDING, null);
        digestRun = digestRunRepository.save(digestRun);
        Long runId = digestRun.getId();

        log.info("[DIGEST_RUN #{}] STAGE 0: Initialized digest pipeline for Channel ID: {}, Name: '{}', Topic: '{}'",
                runId, channel.getId(), channel.getName(), channel.getTopicQuery());

        try {
            // 1. Fetch candidate pool via Phase 3 Research Pipeline
            log.info("[DIGEST_RUN #{}] STAGE 1: Starting Research Pipeline (TinyFish Search & Fetch)...", runId);
            List<RawArticle> candidateArticles = researchPipelineService.executeResearch(channel.getTopicQuery());
            log.info("[DIGEST_RUN #{}] STAGE 1 COMPLETED: Research returned {} candidate articles with full text for channel '{}'",
                    runId, candidateArticles.size(), channel.getName());

            if (candidateArticles.isEmpty()) {
                log.warn("[DIGEST_RUN #{}] STAGE 1 WARNING: No articles were found/fetched for channel '{}'. Marking run as completed with 0 items.",
                        runId, channel.getName());
                digestRun.setStatus(DigestRunStatus.SUCCESS);
                digestRun.setEmailSent(false);
                digestRun = digestRunRepository.save(digestRun);
                return digestRun;
            }

            // 2. Groq Call 1: Cluster & Rank articles
            int targetCount = channel.getArticleCount() != null ? channel.getArticleCount() : 10;
            log.info("[DIGEST_RUN #{}] STAGE 2: Starting Groq Call 1 (Cluster & Rank) - candidates: {}, targetCount: {}",
                    runId, candidateArticles.size(), targetCount);
            List<RawArticle> rankedArticles = llmDigestService.clusterAndRank(candidateArticles, channel.getTopicQuery(), targetCount);
            log.info("[DIGEST_RUN #{}] STAGE 2 COMPLETED: Ranked candidate pool trimmed to top {} articles", runId, rankedArticles.size());

            // 3. Groq Call 2: Synthesize 'why it matters' blurbs
            log.info("[DIGEST_RUN #{}] STAGE 3: Starting Groq Call 2 (Synthesize Blurbs) for {} articles...", runId, rankedArticles.size());
            llmDigestService.synthesizeBlurbs(rankedArticles, channel.getTopicQuery());
            log.info("[DIGEST_RUN #{}] STAGE 3 COMPLETED: Blurbs synthesized for {} articles", runId, rankedArticles.size());

            // 4. Persist NewsItem entities for each ranked story
            log.info("[DIGEST_RUN #{}] STAGE 4: Persisting {} NewsItem entities to Neon database...", runId, rankedArticles.size());
            List<NewsItem> newsItems = new ArrayList<>();
            for (int i = 0; i < rankedArticles.size(); i++) {
                RawArticle article = rankedArticles.get(i);
                NewsItem item = new NewsItem();
                item.setDigestRun(digestRun);
                item.setTitle(article.getTitle() != null ? article.getTitle() : "Untitled");
                item.setUrl(article.getUrl() != null ? article.getUrl() : "");
                item.setSourceName(article.getPublisher());
                item.setSummaryBlurb(article.getSummaryBlurb());
                item.setRankPosition(i + 1);
                item.setPublishedAt(parseInstantOrNull(article.getPublishedAt()));
                newsItems.add(item);
            }

            newsItemRepository.saveAll(newsItems);
            log.info("[DIGEST_RUN #{}] STAGE 4 COMPLETED: {} NewsItem entities saved", runId, newsItems.size());

            // 5. Phase 6: Email Delivery
            log.info("[DIGEST_RUN #{}] STAGE 5: Triggering Email Delivery via Resend...", runId);
            boolean emailSent = false;
            try {
                emailSent = emailService.sendDigestEmail(channel, digestRun, newsItems);
                log.info("[DIGEST_RUN #{}] STAGE 5 COMPLETED: Email send status = {}", runId, emailSent);
            } catch (Exception ex) {
                log.error("[DIGEST_RUN #{}] STAGE 5 ERROR: Failed to send email digest for Channel '{}', research data preserved: {}",
                        runId, channel.getName(), ex.getMessage(), ex);
            }
            digestRun.setEmailSent(emailSent);

            // 6. Update DigestRun status to SUCCESS
            digestRun.setStatus(DigestRunStatus.SUCCESS);
            digestRun = digestRunRepository.save(digestRun);

            long duration = System.currentTimeMillis() - startTime;
            int groqCallsToday = groqUsageTracker != null ? groqUsageTracker.getDailyCallCount() : -1;

            log.info("[DIGEST_RUN #{}] SUMMARY: Status=SUCCESS, Channel='{}', PersistedArticles={}, EmailSent={}, Duration={}ms, GroqDailyUsage={}/1000 RPD",
                    runId, channel.getName(), newsItems.size(), emailSent, duration, groqCallsToday);

            return digestRun;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[DIGEST_RUN #{}] FAILED after {}ms for Channel ID {}: {}", runId, duration, channel.getId(), e.getMessage(), e);
            digestRun.setStatus(DigestRunStatus.FAILED);
            digestRun.setErrorMessage(e.getMessage());
            digestRun = digestRunRepository.save(digestRun);
            return digestRun;
        }
    }

    private Instant parseInstantOrNull(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rawDate);
        } catch (Exception e) {
            return null;
        }
    }
}
