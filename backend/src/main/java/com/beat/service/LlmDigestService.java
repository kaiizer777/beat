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

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));
        String systemPrompt = """
                Current Date: {currentDate}. Evaluate all claims of recency (e.g., "new", "just announced", "latest") strictly relative to this date.
                You are an expert news editor. Your job is to select and rank the top unique, high-quality news articles for a research digest topic.
                1. Identify duplicate stories covering the exact same news event and pick the best single coverage source.
                2. Rank the top unique articles by relevance and RECENCY. Penalize or discard articles that appear outdated relative to the Current Date.
                3. Respond ONLY with a valid JSON object matching this schema:
                {
                  "rankedIndices": [index1, index2, index3, ...]
                }
                Do not include markdown preamble, formatting fences, or any other text outside the JSON object.
                """.replace("{currentDate}", currentDate);

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

        String currentDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(currentInstant.atZone(ZoneOffset.UTC));
        String systemPrompt = """
                Current Date: {currentDate}. Evaluate all claims of recency (e.g., "new", "just announced", "latest") strictly relative to this date.
                You are a senior analyst producing concise news summaries.
                For each provided article in sequence (Article [0], Article [1], etc.), generate a clear 2-3 sentence 'why it matters' synthesis blurb.
                - Base each blurb strictly on the provided text for that article.
                - Do not invent claims, outside knowledge, or speculation.
                - Respond ONLY with a valid JSON object matching this schema:
                {
                  "blurbs": [
                    "2-3 sentence blurb for Article 0...",
                    "2-3 sentence blurb for Article 1..."
                  ]
                }
                """.replace("{currentDate}", currentDate);

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
}
