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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        Instant currentInstant = Instant.now();
        DigestRun digestRun = new DigestRun(channel, currentInstant, DigestRunStatus.PENDING, null);
        digestRun = digestRunRepository.save(digestRun);
        Long runId = digestRun.getId();

        log.info("[DIGEST_RUN #{}] STAGE 0: Initialized digest pipeline for Channel ID: {}, Name: '{}', Topic: '{}'",
                runId, channel.getId(), channel.getName(), channel.getTopicQuery());

        try {
            // Channel-aware freshness window: channel.freshnessWindowDays (default 7) → hours.
            // Falls back to 168h (7d) if a misconfigured channel has the field null.
            int freshnessWindowDays = channel.getFreshnessWindowDays() != null
                    ? channel.getFreshnessWindowDays() : 7;
            int maxAgeHours = freshnessWindowDays * 24;
            // Target count must be resolved before the research call: the broader-search
            // fallback in executeResearch uses it to decide whether to trigger.
            int targetCount = channel.getArticleCount() != null ? channel.getArticleCount() : 10;

            // 1. Fetch candidate pool via Phase 3 Research Pipeline
            log.info("[DIGEST_RUN #{}] STAGE 1: Starting Research Pipeline (TinyFish Search & Fetch)...", runId);
            ResearchResult researchResult = researchPipelineService.executeResearch(
                    channel.getTopicQuery(), maxAgeHours, targetCount);
            List<RawArticle> candidateArticles = researchResult.getArticles();
            Map<String, Object> researchMetrics = researchResult.getMetrics();
            log.info("[DIGEST_RUN #{}] STAGE 1 COMPLETED: Research returned {} candidate articles with full text for channel '{}' (freshnessWindowDays={})",
                    runId, candidateArticles.size(), channel.getName(), freshnessWindowDays);

            if (candidateArticles.isEmpty()) {
                log.warn("[DIGEST_RUN #{}] STAGE 1 WARNING: No articles were found/fetched for channel '{}'. Marking run as completed with 0 items.",
                        runId, channel.getName());
                log.info("[DIGEST_RUN #{}] PIPELINE_METRICS targetCount={} research={} clusterRank=NA synthesize=NA factCheck=NA persisted=0",
                        runId, targetCount, researchMetrics);
                digestRun.setStatus(DigestRunStatus.SUCCESS);
                digestRun.setEmailSent(false);
                digestRun = digestRunRepository.save(digestRun);
                return digestRun;
            }

            // Surface upstream starvation: if the raw candidate pool is smaller than the
            // requested target, the LLM cluster/rank step will be bypassed or thinned,
            // so flag it loudly for diagnostics.
            if (candidateArticles.size() < targetCount) {
                log.warn("[DIGEST_RUN #{}] Candidate pool size ({}) is smaller than targetCount ({}). Upstream research did not yield enough results.",
                        runId, candidateArticles.size(), targetCount);
            }

            // 2. Groq Call 1: Cluster & Rank articles
            // targetCount already computed above for the candidate-pool warning
            log.info("[DIGEST_RUN #{}] STAGE 2: Starting Groq Call 1 (Cluster & Rank) - candidates: {}, targetCount: {}",
                    runId, candidateArticles.size(), targetCount);
            int rankInput = candidateArticles.size();
            List<RawArticle> rankedArticles = llmDigestService.clusterAndRank(candidateArticles, channel.getTopicQuery(), targetCount, currentInstant);
            int rankOutput = rankedArticles.size();
            log.info("[DIGEST_RUN #{}] STAGE 2 COMPLETED: Ranked candidate pool trimmed to top {} articles (input={} output={})",
                    runId, rankedArticles.size(), rankInput, rankOutput);

            // 3. Groq Call 2: Synthesize 'why it matters' blurbs
            log.info("[DIGEST_RUN #{}] STAGE 3: Starting Groq Call 2 (Synthesize Blurbs) for {} articles...", runId, rankedArticles.size());
            int synthesizeInput = rankedArticles.size();
            llmDigestService.synthesizeBlurbs(rankedArticles, channel.getTopicQuery(), currentInstant);
            int synthesizeWithBlurb = (int) rankedArticles.stream()
                    .filter(a -> a.getSummaryBlurb() != null && !a.getSummaryBlurb().isBlank())
                    .count();
            log.info("[DIGEST_RUN #{}] STAGE 3 COMPLETED: Blurbs synthesized for {}/{} articles",
                    runId, synthesizeWithBlurb, synthesizeInput);

            // 3.5 Phase 5: Fact-Checking / Verification Stage
            log.info("[DIGEST_RUN #{}] STAGE 3.5: Starting Fact-Checking for {} articles...", runId, rankedArticles.size());
            List<RawArticle> verifiedArticles = new ArrayList<>();
            int originalCount = rankedArticles.size();
            Map<String, Integer> rejectionReasonCounts = new LinkedHashMap<>();
            for (int i = 0; i < rankedArticles.size(); i++) {
                RawArticle article = rankedArticles.get(i);
                LlmDigestService.VerificationResult verifyResult = llmDigestService.verifyAndRefine(article, article.getSummaryBlurb(), currentInstant);
                if (verifyResult.isAccepted()) {
                    article.setSummaryBlurb(verifyResult.getRefinedBlurb());
                    verifiedArticles.add(article);
                } else {
                    String reason = verifyResult.getRejectionReason() != null ? verifyResult.getRejectionReason() : "unspecified";
                    log.warn("[DIGEST_RUN #{}] Article rejected during fact-checking: '{}'. Reason: {}",
                            runId, article.getTitle(), reason);
                    rejectionReasonCounts.merge(reason, 1, Integer::sum);
                }

                // Rate-limit pause (1000ms throttle between sequential fact-check calls to prevent TPM bursting)
                if (i < rankedArticles.size() - 1) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            int rejectedCount = originalCount - verifiedArticles.size();
            int acceptedCount = verifiedArticles.size();
            log.info("[DIGEST_RUN #{}] STAGE 3.5 COMPLETED: {}/{} articles verified ({} rejected) rejectionReasons={}",
                    runId, acceptedCount, originalCount, rejectedCount, rejectionReasonCounts);

            // Workflow.md Phase 5 step 5: "error budget" — if >50% of articles are
            // rejected, log an ERROR and surface it in digest_run.error_message. This
            // is a soft signal of a systemic prompt or data problem; the run continues
            // with the articles that DID verify so the user still gets a digest.
            if (originalCount > 0 && ((double) rejectedCount / originalCount) > 0.5) {
                String errorMsg = String.format("High rejection rate in fact-checking: %d out of %d articles rejected (>50%%). Possible systemic prompt or data issue.", rejectedCount, originalCount);
                log.error("[DIGEST_RUN #{}] {}", runId, errorMsg);
                digestRun.setErrorMessage(errorMsg);
            }

            rankedArticles = verifiedArticles;

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
                item.setPublishedAt(com.beat.util.DateParserUtils.parseInstantOrNull(article.getPublishedAt()));
                newsItems.add(item);
            }

            newsItemRepository.saveAll(newsItems);
            log.info("[DIGEST_RUN #{}] STAGE 4 COMPLETED: {} NewsItem entities saved", runId, newsItems.size());

            // Single end-of-pipeline metrics block — easiest place to compare
            // funnel widths at each stage. Format mirrors a JSON-ish key=value list.
            Map<String, Object> clusterRankMetrics = new LinkedHashMap<>();
            clusterRankMetrics.put("input", rankInput);
            clusterRankMetrics.put("output", rankOutput);
            clusterRankMetrics.put("dropped", rankInput - rankOutput);
            clusterRankMetrics.put("skippedBecauseBypass", rankInput <= targetCount);

            Map<String, Object> synthesizeMetrics = new LinkedHashMap<>();
            synthesizeMetrics.put("input", synthesizeInput);
            synthesizeMetrics.put("withBlurb", synthesizeWithBlurb);
            synthesizeMetrics.put("withoutBlurb", synthesizeInput - synthesizeWithBlurb);

            Map<String, Object> factCheckMetrics = new LinkedHashMap<>();
            factCheckMetrics.put("input", originalCount);
            factCheckMetrics.put("accepted", acceptedCount);
            factCheckMetrics.put("rejected", rejectedCount);
            factCheckMetrics.put("rejectionReasons", rejectionReasonCounts);

            Map<String, Object> allMetrics = new LinkedHashMap<>();
            allMetrics.put("targetCount", targetCount);
            allMetrics.put("research", researchMetrics);
            allMetrics.put("clusterRank", clusterRankMetrics);
            allMetrics.put("synthesize", synthesizeMetrics);
            allMetrics.put("factCheck", factCheckMetrics);
            allMetrics.put("persisted", newsItems.size());

            log.info("[DIGEST_RUN #{}] PIPELINE_METRICS {}", runId, allMetrics);

            // 5. Phase 6: Email Delivery
            log.info("[DIGEST_RUN #{}] STAGE 5: Triggering Email Delivery via EmailRouter...", runId);
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
}
