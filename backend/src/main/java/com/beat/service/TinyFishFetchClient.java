package com.beat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TinyFishFetchClient {

    private static final Logger log = LoggerFactory.getLogger(TinyFishFetchClient.class);
    private static final String FETCH_API_URL = "https://api.fetch.tinyfish.ai";
    private static final String JINA_READER_PREFIX = "https://r.jina.ai/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public TinyFishFetchClient(@Value("${tinyfish.api-key:${TINYFISH_API_KEY:}}") String apiKey) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(7000);
        this.restTemplate = new RestTemplate(factory);
        org.springframework.http.converter.StringHttpMessageConverter stringConverter =
                new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8);
        stringConverter.setSupportedMediaTypes(List.of(MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.ALL));
        this.restTemplate.getMessageConverters().add(0, stringConverter);
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
    }

    public FetchResult fetchContent(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        // Try TinyFish Fetch first
        FetchResult result = tryTinyFishFetch(url);
        if (result != null && result.getContent() != null && result.getContent().trim().length() > 100) {
            return result;
        }

        // Fallback to Jina AI Reader
        log.info("TinyFish fetch unavailable or returned low content for URL: {}. Retrying with Jina AI Reader...", url);
        FetchResult jinaResult = tryJinaFetch(url);
        if (jinaResult != null && jinaResult.getContent() != null && jinaResult.getContent().trim().length() > 100) {
            return jinaResult;
        }

        log.warn("Both TinyFish Fetch and Jina Reader failed to extract content for URL: {}", url);
        return null;
    }

    private FetchResult tryTinyFishFetch(String url) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("TINYFISH_API_KEY missing, skipping TinyFish fetch");
            return null;
        }

        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-API-Key", apiKey);

                Map<String, Object> bodyMap = new HashMap<>();
                bodyMap.put("urls", List.of(url));
                bodyMap.put("format", "markdown");

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(bodyMap, headers);
                ResponseEntity<String> response = restTemplate.exchange(FETCH_API_URL, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode resultsNode = root.path("results");
                    if (resultsNode.isArray() && !resultsNode.isEmpty()) {
                        JsonNode firstResult = resultsNode.get(0);
                        String status = firstResult.path("status").asText("");
                        String content = firstResult.path("content").asText("");

                        if ("success".equalsIgnoreCase(status) && !content.isBlank()) {
                            return new FetchResult(content, "tinyfish");
                        }
                    }
                }
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("TinyFish Fetch error on attempt {}/{} for {}: {}. Retrying...", attempt, maxAttempts, url, e.getMessage());
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.warn("TinyFish Fetch failed for {} after {} attempts: {}", url, maxAttempts, e.getMessage());
                }
            }
        }
        return null;
    }

    private FetchResult tryJinaFetch(String url) {
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                java.net.URI uri = java.net.URI.create(JINA_READER_PREFIX + url.trim());
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isBlank()) {
                    return new FetchResult(response.getBody(), "jina");
                }
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("Jina AI Reader error on attempt {}/{} for {}: {}. Retrying...", attempt, maxAttempts, url, e.getMessage());
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.warn("Jina AI Reader failed for {} after {} attempts: {}", url, maxAttempts, e.getMessage());
                }
            }
        }
        return null;
    }

    public static class FetchResult {
        private final String content;
        private final String source; // "tinyfish" or "jina"

        public FetchResult(String content, String source) {
            this.content = content;
            this.source = source;
        }

        public String getContent() {
            return content;
        }

        public String getSource() {
            return source;
        }
    }
}
