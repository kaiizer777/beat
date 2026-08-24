package com.beat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-20b";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GroqUsageTracker usageTracker;

    public GroqClient(@Value("${groq.api-key:${GROQ_API_KEY:}}") String apiKey,
                      @Value("${groq.model:${GROQ_MODEL:openai/gpt-oss-20b}}") String model,
                      ObjectMapper objectMapper,
                      GroqUsageTracker usageTracker) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : DEFAULT_MODEL;
        this.objectMapper = objectMapper;
        this.usageTracker = usageTracker;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String generateJsonResponse(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured in environment or application.yml");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", this.model);

        ArrayNode messages = requestBody.putArray("messages");

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        requestBody.put("temperature", 0.2);

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        int maxRetries = 3;
        long waitTimeMs = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GROQ_API_URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    if (usageTracker != null) {
                        usageTracker.recordCall();
                    }
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
                    if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                        throw new IOException("Groq API returned empty content in response choice");
                    }
                    return cleanJsonString(contentNode.asText());
                } else if (statusCode == 429) {
                    log.warn("Groq API rate limited (429) on attempt {}/{} - backing off for {} ms", attempt, maxRetries, waitTimeMs);
                    if (attempt == maxRetries) {
                        throw new IOException("Groq API rate limit exceeded after " + maxRetries + " attempts. Status 429: " + response.body());
                    }
                    Thread.sleep(waitTimeMs);
                    waitTimeMs *= 2;
                } else {
                    throw new IOException("Groq API request failed with status code " + statusCode + ": " + response.body());
                }
            } catch (IOException e) {
                if (attempt < maxRetries) {
                    log.warn("Transient network error calling Groq API on attempt {}/{}: {}. Retrying in {} ms...",
                            attempt, maxRetries, e.getMessage(), waitTimeMs);
                    Thread.sleep(waitTimeMs);
                    waitTimeMs *= 2;
                } else {
                    log.error("Groq API call failed after {} attempts due to network error: {}", maxRetries, e.getMessage());
                    throw e;
                }
            }
        }

        throw new IOException("Failed to communicate with Groq API");
    }

    private String cleanJsonString(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}

