package com.beat.service;

import com.beat.dto.RawArticle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LlmDigestService {

    private static final Logger log = LoggerFactory.getLogger(LlmDigestService.class);

    private final GroqClient groqClient;
    private final ObjectMapper objectMapper;

    public LlmDigestService(GroqClient groqClient, ObjectMapper objectMapper) {
        this.groqClient = groqClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Call 1: Cluster & Rank candidate articles using Groq LLM (Title + Snippet only).
     */
    public List<RawArticle> clusterAndRank(List<RawArticle> candidates, String topicQuery, int targetCount, Instant currentInstant) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        if (candidates.size() <= targetCount) {
            log.info("Candidate count ({}) is <= targetCount ({}), skipping LLM clustering call", candidates.size(), targetCount);
            return new ArrayList<>(candidates);
        }

        log.info("Executing Groq LLM Call 1 (Cluster & Rank) for {} candidates on topic: '{}'", candidates.size(), topicQuery);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Topic: ").append(topicQuery).append("\n");
        userPrompt.append("Target Article Count: ").append(targetCount).append("\n\n");
        userPrompt.append("Candidate Articles List:\n");

        for (int i = 0; i < candidates.size(); i++) {
            RawArticle article = candidates.get(i);
            userPrompt.append("[").append(i).append("] Title: ").append(article.getTitle()).append("\n");
            userPrompt.append("    Source: ").append(article.getPublisher() != null ? article.getPublisher() : "Unknown").append("\n");
            userPrompt.append("    Snippet: ").append(article.getSnippet() != null ? article.getSnippet() : "").append("\n\n");
        }

        StringBuilder exampleIndices = new StringBuilder("[");
        for (int i = 0; i < targetCount; i++) {
            exampleIndices.append(i);
            if (i < targetCount - 1) exampleIndices.append(", ");
        }
        exampleIndices.append("]");

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));
        String systemPrompt = """
                Current Date: {currentDate}. Evaluate all claims of recency (e.g., "new", "just announced", "latest") strictly relative to this date.
                You are an expert news editor. Your job is to select and rank the top unique, high-quality news articles for a research digest topic.
                1. Identify duplicate stories covering the exact same news event and pick the best single coverage source.
                2. Rank the top unique articles by relevance and RECENCY. Penalize or discard articles that appear outdated relative to the Current Date.
                3. You MUST select EXACTLY {targetCount} articles (unless there are fewer valid candidates available).
                4. Respond ONLY with a valid JSON object matching this schema:
                {
                  "rankedIndices": {exampleIndices}
                }
                Do not include markdown preamble, formatting fences, or any other text outside the JSON object.
                """.replace("{currentDate}", currentDate)
                   .replace("{targetCount}", String.valueOf(targetCount))
                   .replace("{exampleIndices}", exampleIndices.toString());

        try {
            String jsonResult = groqClient.generateJsonResponse(systemPrompt, userPrompt.toString());
            JsonNode root = objectMapper.readTree(jsonResult);
            JsonNode indicesNode = root.path("rankedIndices");

            List<RawArticle> ranked = new ArrayList<>();
            if (indicesNode.isArray()) {
                for (JsonNode indexItem : indicesNode) {
                    if (indexItem.isInt()) {
                        int idx = indexItem.asInt();
                        if (idx >= 0 && idx < candidates.size()) {
                            RawArticle selected = candidates.get(idx);
                            if (!ranked.contains(selected)) {
                                ranked.add(selected);
                            }
                        }
                    }
                    if (ranked.size() >= targetCount) {
                        break;
                    }
                }
            }

            if (!ranked.isEmpty()) {
                log.info("Groq Call 1 (Cluster & Rank) successfully selected {} top articles", ranked.size());
                return ranked;
            } else {
                log.warn("Groq Call 1 returned empty/invalid rankedIndices. Falling back to default order");
            }
        } catch (Exception e) {
            log.error("Groq Call 1 (Cluster & Rank) failed, falling back to top candidate slice: {}", e.getMessage(), e);
        }

        // Fallback: take top targetCount candidates in original order
        return candidates.subList(0, Math.min(candidates.size(), targetCount));
    }

    /**
     * Call 2: Synthesize 2-3 sentence "Why It Matters" blurbs using Groq LLM (Full Text).
     */
    public void synthesizeBlurbs(List<RawArticle> rankedArticles, String topicQuery, Instant currentInstant) {
        if (rankedArticles == null || rankedArticles.isEmpty()) {
            return;
        }

        log.info("Executing Groq LLM Call 2 (Synthesize Blurbs) for {} ranked articles on topic: '{}'", rankedArticles.size(), topicQuery);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Topic: ").append(topicQuery).append("\n\n");
        userPrompt.append("Articles to summarize:\n");

        for (int i = 0; i < rankedArticles.size(); i++) {
            RawArticle article = rankedArticles.get(i);
            userPrompt.append("Article [").append(i).append("]:\n");
            userPrompt.append("Title: ").append(article.getTitle()).append("\n");
            userPrompt.append("Source: ").append(article.getPublisher() != null ? article.getPublisher() : "Unknown").append("\n");
            
            String text = article.getFullText();
            if (text == null || text.isBlank()) {
                text = article.getSnippet();
            }
            if (text != null && text.length() > 1500) {
                text = text.substring(0, 1500) + "...";
            }
            userPrompt.append("Content: ").append(text != null ? text : "").append("\n\n");
        }

        StringBuilder exampleBlurbs = new StringBuilder("[\n");
        for (int i = 0; i < rankedArticles.size(); i++) {
            exampleBlurbs.append("    \"2-3 sentence blurb for Article [").append(i).append("]\"");
            if (i < rankedArticles.size() - 1) exampleBlurbs.append(",");
            exampleBlurbs.append("\n");
        }
        exampleBlurbs.append("  ]");

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));
        String systemPrompt = """
                Current Date: {currentDate}. Evaluate all claims of recency (e.g., "new", "just announced", "latest") strictly relative to this date.
                You are a senior analyst producing concise news summaries.
                For each provided article in sequence (Article [0], Article [1], etc.), generate a clear 2-3 sentence 'why it matters' synthesis blurb.
                
                CRITICAL RULES:
                - Do NOT extrapolate, infer, or invent model versions, software names, release dates, or statistics.
                - Every claim in the blurb MUST be directly derivable from the provided source text.
                - The blurb must be 100% derivative of the provided text. No external knowledge.
                
                - Base each blurb strictly on the provided text for that article.
                - Do not invent claims, outside knowledge, or speculation.
                - Respond ONLY with a valid JSON object matching this schema:
                {
                  "blurbs": {exampleBlurbs}
                }
                """.replace("{currentDate}", currentDate)
                   .replace("{exampleBlurbs}", exampleBlurbs.toString());

        try {
            String jsonResult = groqClient.generateJsonResponse(systemPrompt, userPrompt.toString());
            JsonNode root = objectMapper.readTree(jsonResult);
            JsonNode blurbsNode = root.path("blurbs");

            if (blurbsNode.isArray()) {
                for (int i = 0; i < rankedArticles.size() && i < blurbsNode.size(); i++) {
                    String blurb = blurbsNode.get(i).asText();
                    if (blurb != null && !blurb.isBlank()) {
                        rankedArticles.get(i).setSummaryBlurb(blurb.trim());
                    }
                }
                log.info("Groq Call 2 (Synthesize Blurbs) completed successfully for {} articles", blurbsNode.size());
                return;
            } else {
                log.warn("Groq Call 2 returned non-array blurbs. Falling back to snippets");
            }
        } catch (Exception e) {
            log.error("Groq Call 2 (Synthesize Blurbs) failed: {}", e.getMessage(), e);
        }

        // Fallback: set summary blurb to snippet if synthesis failed
        for (RawArticle article : rankedArticles) {
            if (article.getSummaryBlurb() == null || article.getSummaryBlurb().isBlank()) {
                article.setSummaryBlurb(article.getSnippet());
            }
        }
    }

    /**
     * Call 3: Fact-Check & Verify a generated blurb against the source text.
     *
     * @return VerificationResult with the (possibly refined) blurb on success, or a
     *         rejectionReason on failure. Callers should check {@code isAccepted()}
     *         to decide whether to keep the article in the digest.
     */
    public VerificationResult verifyAndRefine(RawArticle article, String generatedBlurb, Instant currentInstant) {
        log.info("Executing Groq LLM Call 3 (Fact-Check) for article: '{}'", article.getTitle());

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));
        String systemPrompt = "You are a strict fact-checker. Current Date: {currentDate}.".replace("{currentDate}", currentDate);

        String text = article.getFullText();
        if (text == null || text.isBlank()) {
            text = article.getSnippet();
        }
        if (text != null && text.length() > 2000) {
            text = text.substring(0, 2000) + "...";
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Source Text:\n").append(text != null ? text : "").append("\n\n");
        userPrompt.append("Generated Blurb:\n").append(generatedBlurb).append("\n\n");
        userPrompt.append("""
                Instructions:
                1. Verify that no numbers, model versions, or named entities appear in the blurb that are absent from the source text.
                2. Verify that timeline claims are consistent with the Current Date.

                CRITICAL RULES FOR REFINED BLURB:
                - If you generate a refinedBlurb, do NOT extrapolate, infer, or invent model versions, software names, release dates, or statistics.
                - Every claim in the refinedBlurb MUST be directly derivable from the provided source text.
                - The refinedBlurb must be 100% derivative of the provided text. No external knowledge.

                3. Respond ONLY with a valid JSON object matching this schema:
                {
                  "isValid": boolean,
                  "refinedBlurb": "corrected text or null",
                  "rejectionReason": "..."
                }
                """);

        try {
            String jsonResult = groqClient.generateJsonResponse(systemPrompt, userPrompt.toString());
            JsonNode root = objectMapper.readTree(jsonResult);

            boolean isValid = root.path("isValid").asBoolean(true);
            JsonNode refinedNode = root.path("refinedBlurb");
            String refinedBlurb = (refinedNode.isNull() || refinedNode.isMissingNode()) ? null : refinedNode.asText();
            String rejectionReason = root.path("rejectionReason").asText(null);

            // Accept: LLM says valid (no refined blurb) -> use original.
            if (isValid && (refinedBlurb == null || refinedBlurb.isBlank())) {
                return VerificationResult.accepted(generatedBlurb);
            }

            // Accept: LLM provided a refined blurb (regardless of isValid flag) -> use refined.
            if (refinedBlurb != null && !refinedBlurb.isBlank()) {
                return VerificationResult.accepted(refinedBlurb.trim());
            }

            // Reject: invalid and no refined blurb available.
            log.warn("Fact-check failed and no refined blurb provided. Reason: {}", rejectionReason);
            return VerificationResult.rejected(rejectionReason != null ? rejectionReason : "unspecified");

        } catch (Exception e) {
            log.error("Groq Call 3 (Fact-Check) failed for article '{}': {}", article.getTitle(), e.getMessage(), e);
            return VerificationResult.rejected("fact-check call failed: " + e.getMessage());
        }
    }

    /**
     * Result of a single fact-check invocation. Either carries an accepted blurb
     * (possibly refined), or a rejection reason.
     */
    public static final class VerificationResult {
        private final String refinedBlurb;
        private final String rejectionReason;

        private VerificationResult(String refinedBlurb, String rejectionReason) {
            this.refinedBlurb = refinedBlurb;
            this.rejectionReason = rejectionReason;
        }

        public static VerificationResult accepted(String refinedBlurb) {
            return new VerificationResult(refinedBlurb, null);
        }

        public static VerificationResult rejected(String rejectionReason) {
            return new VerificationResult(null, rejectionReason);
        }

        public boolean isAccepted() {
            return refinedBlurb != null;
        }

        public String getRefinedBlurb() {
            return refinedBlurb;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }
    }
}
