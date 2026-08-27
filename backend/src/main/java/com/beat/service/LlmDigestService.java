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
import java.util.Set;

@Service
public class LlmDigestService {

    private static final Logger log = LoggerFactory.getLogger(LlmDigestService.class);

    private final GroqClient groqClient;
    private final ObjectMapper objectMapper;

    // L3: Tier-1 source credibility set for ranking signal
    private static final Set<String> TIER1_SOURCES = Set.of(
        "reuters", "associated press", "ap", "bloomberg", "the new york times", "nytimes",
        "the washington post", "wsj", "wall street journal", "financial times", "ft",
        "techcrunch", "the verge", "wired", "ars technica", "mit technology review",
        "nature", "science", "bbc", "the guardian", "axios", "politico", "forbes",
        "cnbc", "cnn", "the economist"
    );

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
            String publisher = article.getPublisher() != null ? article.getPublisher() : "Unknown";
            // L3: Annotate tier-1 sources
            String tierBadge = isTier1Source(publisher) ? " [Tier-1]" : "";
            userPrompt.append("[").append(i).append("] Title: ").append(article.getTitle()).append("\n");
            userPrompt.append("    Source: ").append(publisher).append(tierBadge).append("\n");
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
                3. Prefer articles from [Tier-1] sources when quality is otherwise equal.
                4. You MUST select EXACTLY {targetCount} articles (unless there are fewer valid candidates available).
                5. Respond ONLY with a valid JSON object matching this schema:
                {
                  "rankedIndices": {exampleIndices}
                }
                Do not include markdown preamble, formatting fences, or any other text outside the JSON object.
                """.replace("{currentDate}", currentDate)
                   .replace("{targetCount}", String.valueOf(targetCount))
                   .replace("{exampleIndices}", exampleIndices.toString());

        try {
            // L5: cap output to 300 tokens for cluster/rank — only needs a short JSON array
            String jsonResult = groqClient.generateJsonResponse(systemPrompt, userPrompt.toString(), 300);
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
            // L1: Raised from 700 → 3000 chars so the model sees ~500 words of real content
            if (text != null && text.length() > 3000) {
                text = text.substring(0, 3000) + "...";
            }
            userPrompt.append("Content: ").append(text != null ? text : "").append("\n\n");
        }

        // Schema placeholders only. The previous version of this code used a long
        // repeated real-example in the schema (15 copies of a 50-word blurb). That ate
        // the output token budget and made the model produce 1-sentence blurbs even
        // though the prompt said "2-3 sentences". Short placeholders + a clear sentence
        // in the system prompt is the sweet spot.
        StringBuilder exampleBlurbs = new StringBuilder("[\n");
        for (int i = 0; i < rankedArticles.size(); i++) {
            exampleBlurbs.append("    \"2-3 sentence blurb for Article [").append(i).append("]...\"");
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
                - If the source text is a short snippet, the blurb should still be 2-3 sentences — use the headline and source to make sense of the snippet, but never invent details.

                - Base each blurb strictly on the provided text for that article.
                - Do not invent claims, outside knowledge, or speculation.
                - Respond ONLY with a valid JSON object matching this schema:
                {
                  "blurbs": {exampleBlurbs}
                }
                """.replace("{currentDate}", currentDate)
                   .replace("{exampleBlurbs}", exampleBlurbs.toString());

        try {
            // L5: cap synthesis output — 150 tokens/article × N articles + overhead
            int maxTokens = Math.max(2500, rankedArticles.size() * 160);
            String jsonResult = groqClient.generateJsonResponse(systemPrompt, userPrompt.toString(), maxTokens);
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

                // Quality safety net: llama-3.3-70b-versatile occasionally emits 1-sentence
                // blurbs (~25 words) instead of the 2-3 sentences the prompt asked for. Detect
                // any short blurb and re-call Groq with a focused "expand this" prompt. Targeted
                // re-calls are cheap (one article at a time) and only fire when needed.
                int expanded = 0;
                for (int i = 0; i < rankedArticles.size() && i < blurbsNode.size(); i++) {
                    RawArticle article = rankedArticles.get(i);
                    String current = article.getSummaryBlurb();
                    if (current == null || isShortBlurb(current)) {
                        String expanded_ = expandShortBlurb(article, current, currentInstant);
                        if (expanded_ != null && !expanded_.isBlank()) {
                            article.setSummaryBlurb(expanded_);
                            expanded++;
                        }
                    }
                }
                if (expanded > 0) {
                    log.info("Synthesize safety net: expanded {} short blurb(s) via targeted re-call", expanded);
                }
                return;
            } else {
                log.warn("Groq Call 2 returned non-array blurbs. Falling back to snippets");
            }
        } catch (Exception e) {
            log.error("Groq Call 2 (Synthesize Blurbs) failed: {}", e.getMessage(), e);
        }

        // Fallback: when synthesis failed or returned no blurbs, use the search snippet
        // as the base. The snippet is typically 1-2 sentences (~120-140 chars), which is
        // too short for the digest UI. If we landed on a short blurb via this path, expand
        // it with a clean factual 2nd sentence so the user gets the "2-3 sentence" reading
        // experience even when the LLM is unavailable.
        for (RawArticle article : rankedArticles) {
            if (article.getSummaryBlurb() == null || article.getSummaryBlurb().isBlank()) {
                String snippet = article.getSnippet();
                if (snippet != null && !snippet.isBlank()) {
                    article.setSummaryBlurb(expandShortSnippet(snippet, article));
                }
            }
        }
    }

    /**
     * Wrap a short search snippet into a 2-sentence blurb by appending a clean,
     * factual summary line. Used only as a fallback when the synthesize LLM call
     * failed (typically due to Groq TPM exhaustion). Kept strictly factual and
     * derived only from the headline and publisher to avoid triggering fact-checker
     * rejections on fabricated phrases.
     */
    String expandShortSnippet(String snippet, RawArticle article) {
        String trimmed = snippet.trim();
        // Ensure snippet ends with terminal punctuation so the appended sentence reads naturally.
        char last = trimmed.isEmpty() ? '.' : trimmed.charAt(trimmed.length() - 1);
        if (last != '.' && last != '!' && last != '?') {
            trimmed = trimmed + ".";
        }
        String publisher = (article != null && article.getPublisher() != null && !article.getPublisher().isBlank())
                ? article.getPublisher() : "the source";
        return trimmed + " Full reporting and continuing coverage are provided by " + publisher + ".";
    }

    /**
     * Heuristic: a blurb is "short" if it has fewer than 25 words OR fewer than 1 sentence.
     * A 2-sentence 35-word technical blurb is valid and does not trigger expansion.
     */
    boolean isShortBlurb(String blurb) {
        if (blurb == null || blurb.isBlank()) return true;
        String trimmed = blurb.trim();
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) return true;  // mid-thought truncation
        // Count sentences by terminal punctuation.
        int sentences = trimmed.split("[.!?]+").length;
        if (sentences < 1) return true;
        String[] words = trimmed.split("\\s+");
        return words.length < 25;
    }

    /**
     * Safety-net re-call for a blurb that the main synth run under-emitted. Asks the
     * model to expand the specific blurb to 2-3 sentences while staying 100% derivative
     * of the source text. Returns the expanded blurb, or null on failure (caller keeps
     * the short one — better than dropping the article).
     */
    private String expandShortBlurb(RawArticle article, String shortBlurb, Instant currentInstant) {
        String text = article.getFullText();
        if (text == null || text.isBlank()) {
            text = article.getSnippet();
        }
        // L1: Match the 3000-char limit used in synthesizeBlurbs
        if (text != null && text.length() > 3000) {
            text = text.substring(0, 3000) + "...";
        }

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));
        String systemPrompt = """
                Current Date: {currentDate}.
                You are expanding a too-short blurb. The blurb below was generated for a news article but
                only covers 1 sentence. Rewrite it as 2-3 complete sentences (50-90 words) that:
                - Sentence 1: a concrete fact drawn from the source text.
                - Sentence 2: why this is significant to a professional reader.
                - Sentence 3 (optional): broader implication or context.
                Stay 100% derivative of the source text. No external knowledge. No invented stats, models, or
                numbers. Never end with "..." or "and more". Finish every thought.

                Respond ONLY with a valid JSON object: {"blurb": "<the expanded blurb>"}
                """.replace("{currentDate}", currentDate);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Title: ").append(article.getTitle()).append("\n");
        userPrompt.append("Source: ").append(article.getPublisher() != null ? article.getPublisher() : "Unknown").append("\n\n");
        userPrompt.append("Source Text:\n").append(text != null ? text : "").append("\n\n");
        userPrompt.append("Current (too short) blurb: ").append(shortBlurb != null ? shortBlurb : "").append("\n");

        try {
            String jsonResult = groqClient.generateJsonResponse(systemPrompt, userPrompt.toString());
            JsonNode root = objectMapper.readTree(jsonResult);
            JsonNode blurbNode = root.path("blurb");
            if (blurbNode.isMissingNode()) {
                // Some models might wrap in array; tolerate either shape.
                JsonNode alt = root.path("blurbs");
                if (alt.isArray() && alt.size() > 0) blurbNode = alt.get(0);
            }
            if (!blurbNode.isMissingNode()) {
                String expanded = blurbNode.asText().trim();
                if (!expanded.isBlank() && !isShortBlurb(expanded)) {
                    return expanded;
                }
            }
        } catch (Exception e) {
            log.warn("expandShortBlurb failed for '{}': {}", article.getTitle(), e.getMessage());
        }
        return null;
    }

    /**
     * L2: Batch fact-check — issues a SINGLE Groq call for all (source, blurb) pairs.
     * This replaces 15 sequential per-article verifyAndRefine calls (one per article)
     * which was exhausting the 100k TPD limit, leaving the last 4 articles unchecked.
     *
     * <p>The method mutates the input list's blurbs in-place (same pattern as synthesizeBlurbs)
     * and returns the subset of articles that passed verification.</p>
     *
     * <p>On any transport/API failure the original blurbs are kept (same fallback policy as
     * the original per-article verifyAndRefine).</p>
     */
    public List<RawArticle> batchVerifyAndRefine(List<RawArticle> articles, Instant currentInstant) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        List<RawArticle> articlesToVerify = new ArrayList<>();
        for (RawArticle article : articles) {
            if (article.getFetchedContent() == null || article.getFetchedContent().length() < 400) {
                log.info("Skipping fact-check for snippet-only article: '{}'", article.getTitle());
            } else {
                articlesToVerify.add(article);
            }
        }

        if (articlesToVerify.isEmpty()) {
            log.info("Groq Call 3 (Batch Fact-Check): all {} articles are snippet-only, accepted as-is", articles.size());
            return new ArrayList<>(articles);
        }

        log.info("Executing Groq LLM Call 3 (Batch Fact-Check) for {}/{} articles with fetched content in a single call",
                articlesToVerify.size(), articles.size());

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));

        String systemPrompt = """
                Current Date: {currentDate}. You are a strict fact-checker.
                You will receive N (source_text, blurb) pairs. For each pair at index i:
                - VALID: blurb contains only claims derivable from source_text (paraphrasing OK).
                - INVALID: blurb contains numbers, statistics, named entities, or model versions absent from or contradicting the source.
                Common journalistic framing ("growing role", "significant implications", "raising concerns") is always VALID.
                If INVALID and fixable, provide refinedBlurb. Otherwise set refinedBlurb to null.

                Respond ONLY with valid JSON:
                {"results": [{"isValid": true, "refinedBlurb": "..." or null}, ...]}
                The array length MUST equal the number of items sent.
                """.replace("{currentDate}", currentDate);

        StringBuilder userPrompt = new StringBuilder();
        for (int i = 0; i < articlesToVerify.size(); i++) {
            RawArticle article = articlesToVerify.get(i);
            String text = article.getFullText();
            if (text == null || text.isBlank()) {
                text = article.getSnippet();
            }
            // L2/L1: 4000-char source window so claims are actually verifiable
            if (text != null && text.length() > 4000) {
                text = text.substring(0, 4000) + "...";
            }
            userPrompt.append("Item [").append(i).append("]:\n");
            userPrompt.append("Source: ").append(text != null ? text : "").append("\n");
            userPrompt.append("Blurb: ").append(article.getSummaryBlurb() != null ? article.getSummaryBlurb() : "").append("\n\n");
        }

        try {
            // L5: cap output — each result is a short JSON object, ~50-100 tokens × N + overhead
            int maxTokens = Math.max(800, articles.size() * 120);
            String jsonResult = groqClient.generateJsonResponse(systemPrompt, userPrompt.toString(), maxTokens);
            JsonNode root = objectMapper.readTree(jsonResult);
            JsonNode resultsNode = root.path("results");

            if (!resultsNode.isArray() || resultsNode.size() != articlesToVerify.size()) {
                log.warn("Batch fact-check: response array size mismatch (expected={}, got={}). Accepting all blurbs as-is.",
                        articlesToVerify.size(), resultsNode.size());
                return new ArrayList<>(articles);
            }

            Set<RawArticle> rejectedSet = new java.util.HashSet<>();
            for (int i = 0; i < articlesToVerify.size(); i++) {
                RawArticle article = articlesToVerify.get(i);
                JsonNode result = resultsNode.get(i);
                boolean isValid = result.path("isValid").asBoolean(true);
                JsonNode refinedNode = result.path("refinedBlurb");
                String refinedBlurb = (refinedNode.isNull() || refinedNode.isMissingNode()) ? null : refinedNode.asText();

                if (refinedBlurb != null && !refinedBlurb.isBlank()) {
                    // LLM provided a fix — use it
                    article.setSummaryBlurb(refinedBlurb.trim());
                    log.debug("Batch fact-check: article [{}] refined by LLM: '{}'", i, article.getTitle());
                } else if (isValid) {
                    // LLM says valid, original blurb stands
                } else {
                    // isValid=false and no refinedBlurb — genuine model rejection
                    String reason = result.path("rejectionReason").asText("unspecified");
                    log.warn("Batch fact-check: article [{}] rejected. Title='{}', Reason={}",
                            i, article.getTitle(), reason);
                    rejectedSet.add(article);
                }
            }

            List<RawArticle> accepted = new ArrayList<>();
            for (RawArticle article : articles) {
                if (!rejectedSet.contains(article)) {
                    accepted.add(article);
                }
            }

            log.info("Groq Call 3 (Batch Fact-Check) completed: {}/{} articles accepted", accepted.size(), articles.size());
            return accepted;

        } catch (Exception e) {
            // Transport / API / JSON-parse failure — fall back to all articles with original blurbs
            log.warn("Groq Call 3 (Batch Fact-Check) failed ({}). Falling back to all synthesized blurbs.", e.getMessage());
            return new ArrayList<>(articles);
        }
    }

    /**
     * Call 3 (single-article): Fact-Check & Verify a generated blurb against the source text.
     * Kept for backward compat / single-article callers. DigestPipelineService uses
     * batchVerifyAndRefine instead.
     */
    public VerificationResult verifyAndRefine(RawArticle article, String generatedBlurb, Instant currentInstant) {
        log.info("Executing Groq LLM Call 3 (Fact-Check) for article: '{}'", article.getTitle());

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));
        String systemPrompt = "You are a strict fact-checker. Current Date: {currentDate}.".replace("{currentDate}", currentDate);

        String text = article.getFullText();
        if (text == null || text.isBlank()) {
            text = article.getSnippet();
        }
        // L1: Raised from 2000 → 4000 chars so claims are actually verifiable
        if (text != null && text.length() > 4000) {
            text = text.substring(0, 4000) + "...";
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Source Text:\n").append(text != null ? text : "").append("\n\n");
        userPrompt.append("Generated Blurb:\n").append(generatedBlurb).append("\n\n");
        userPrompt.append("""
                Instructions:
                1. Verify that no specific numbers, model versions, or named entities appear in the blurb that are absent from the source text.
                2. Verify that timeline claims are consistent with the Current Date.

                A blurb is INVALID only when it contains:
                - Specific numbers, statistics, or model versions that are absent from or contradict the source.
                - Named entities (people, companies, products) that are not in the source.
                - Timeline claims that contradict the source or are inconsistent with the Current Date.
                - A direct factual contradiction with the source.

                A blurb is VALID when it:
                - Paraphrases or synthesises information from the source (paraphrasing is acceptable).
                - Uses common journalistic framing ("growing role", "significant implications", "raising concerns", "valuable insights", "broader market", etc.) that is not present verbatim in the source but is consistent with the source's overall message.
                - Highlights the most newsworthy aspect of the source.

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
            // Transport / API / JSON-parse failure is NOT a model rejection. Fall
            // back to the synthesized blurb (which already passed the synthesize
            // stage's strict derivative rules) and log loudly. The workflow.md
            // error budget will still pick up persistent systematic issues via
            // the WARN reason log in the orchestrator.
            log.warn("Groq Call 3 (Fact-Check) call failed for article '{}' ({}). Falling back to synthesized blurb.",
                    article.getTitle(), e.getMessage());
            return VerificationResult.accepted(generatedBlurb);
        }
    }

    /**
     * L3: Check if a publisher is a tier-1 credibility source.
     */
    private boolean isTier1Source(String publisher) {
        if (publisher == null || publisher.isBlank()) return false;
        String lower = publisher.toLowerCase();
        return TIER1_SOURCES.stream().anyMatch(lower::contains);
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
