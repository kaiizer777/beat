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
        return generateJsonResponse(systemPrompt, userPrompt, -1);
    }

    /**
     * L5: Overload with max_tokens cap. Pass -1 for no cap (existing behaviour).
     */
    public String generateJsonResponse(String systemPrompt, String userPrompt, int maxTokens) throws IOException, InterruptedException {
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

        if (maxTokens > 0) {
            requestBody.put("max_tokens", maxTokens);
        }

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        int maxRetries = 4;
        long waitTimeMs = 5000;

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
                    long delayMs = extractRetryDelayMs(response, waitTimeMs);
                    log.warn("Groq API rate limited (429) on attempt {}/{} - backing off for {} ms", attempt, maxRetries, delayMs);
                    if (attempt == maxRetries) {
                        throw new IOException("Groq API rate limit exceeded after " + maxRetries + " attempts. Status 429: " + response.body());
                    }
                    Thread.sleep(delayMs);
                    waitTimeMs = Math.min(Math.max(delayMs, waitTimeMs * 2), 15000);
                } else {
                    throw new IOException("Groq API request failed with status code " + statusCode + ": " + response.body());
                }
            } catch (IOException e) {
                if (attempt < maxRetries) {
                    log.warn("Transient network error calling Groq API on attempt {}/{}: {}. Retrying in {} ms...",
                            attempt, maxRetries, e.getMessage(), waitTimeMs);
                    Thread.sleep(waitTimeMs);
                    waitTimeMs = Math.min(waitTimeMs * 2, 15000);
                } else {
                    log.error("Groq API call failed after {} attempts due to network error: {}", maxRetries, e.getMessage());
                    throw e;
                }
            }
        }

        throw new IOException("Failed to communicate with Groq API");
    }

    private static final java.util.regex.Pattern RESET_TIME_PATTERN =
            java.util.regex.Pattern.compile("try again in (\\d+(?:\\.\\d+)?)\\s*(m?s|minutes?|seconds?)?", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Determine how long to wait before retrying after an HTTP 429 response.
     * Inspects Retry-After, x-ratelimit-reset-tokens, x-ratelimit-reset-requests headers,
     * and the response body error message.
     */
    long extractRetryDelayMs(HttpResponse<String> response, long defaultWaitMs) {
        if (response == null) {
            return defaultWaitMs;
        }

        // 1. Check Retry-After header
        java.util.Optional<String> retryAfterHeader = response.headers().firstValue("retry-after");
        if (retryAfterHeader.isPresent() && !retryAfterHeader.get().isBlank()) {
            long parsed = parseSecondsOrDurationToMs(retryAfterHeader.get());
            if (parsed > 0) {
                return parsed + 500; // Add 500ms safety buffer
            }
        }

        // 2. Check x-ratelimit-reset-tokens header (e.g. "28.35s", "28s", "28350ms")
        java.util.Optional<String> resetTokensHeader = response.headers().firstValue("x-ratelimit-reset-tokens");
        if (resetTokensHeader.isPresent() && !resetTokensHeader.get().isBlank()) {
            long parsed = parseSecondsOrDurationToMs(resetTokensHeader.get());
            if (parsed > 0) {
                return parsed + 500;
            }
        }

        // 3. Check x-ratelimit-reset-requests header
        java.util.Optional<String> resetRequestsHeader = response.headers().firstValue("x-ratelimit-reset-requests");
        if (resetRequestsHeader.isPresent() && !resetRequestsHeader.get().isBlank()) {
            long parsed = parseSecondsOrDurationToMs(resetRequestsHeader.get());
            if (parsed > 0) {
                return parsed + 500;
            }
        }

        // 4. Parse from response body message
        String body = response.body();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                String errorMsg = root.path("error").path("message").asText("");
                if (!errorMsg.isBlank()) {
                    long parsed = parseDelayFromMessage(errorMsg);
                    if (parsed > 0) {
                        return parsed + 500;
                    }
                }
            } catch (Exception ignored) {
                long parsed = parseDelayFromMessage(body);
                if (parsed > 0) {
                    return parsed + 500;
                }
            }
        }

        return defaultWaitMs;
    }

    private long parseDelayFromMessage(String msg) {
        java.util.regex.Matcher matcher = RESET_TIME_PATTERN.matcher(msg);
        if (matcher.find()) {
            try {
                double val = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2);
                if (unit != null && (unit.equalsIgnoreCase("ms") || unit.equalsIgnoreCase("millisecond") || unit.equalsIgnoreCase("milliseconds"))) {
                    return (long) val;
                } else if (unit != null && (unit.toLowerCase().startsWith("m") && !unit.toLowerCase().startsWith("ms"))) {
                    return (long) (val * 60_000);
                } else {
                    return (long) (val * 1000);
                }
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private long parseSecondsOrDurationToMs(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String trimmed = raw.trim().toLowerCase();
        try {
            if (trimmed.endsWith("ms")) {
                return (long) Double.parseDouble(trimmed.substring(0, trimmed.length() - 2).trim());
            } else if (trimmed.endsWith("s")) {
                return (long) (Double.parseDouble(trimmed.substring(0, trimmed.length() - 1).trim()) * 1000);
            } else if (trimmed.endsWith("m")) {
                return (long) (Double.parseDouble(trimmed.substring(0, trimmed.length() - 1).trim()) * 60_000);
            } else {
                return (long) (Double.parseDouble(trimmed) * 1000);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
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

