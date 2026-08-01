package com.beat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class TinyFishClient {

    private static final Logger log = LoggerFactory.getLogger(TinyFishClient.class);
    private static final String SEARCH_API_URL = "https://api.search.tinyfish.ai";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public TinyFishClient(@Value("${tinyfish.api-key:${TINYFISH_API_KEY:}}") String apiKey) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
    }

    public List<SearchResultItem> searchNews(String query) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("TINYFISH_API_KEY is not configured!");
            return List.of();
        }

        // Build URI object directly to avoid double-encoding from toUriString()+exchange(String)
        java.net.URI requestUri = UriComponentsBuilder.fromHttpUrl(SEARCH_API_URL)
                .queryParam("query", query)
                .queryParam("domain_type", "news")
                .build().encode().toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        int maxRetries = 3;
        long backoffMs = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("TinyFish Search calling: query='{}' uri='{}' (attempt {}/{})", query, requestUri, attempt, maxRetries);
                ResponseEntity<String> response = restTemplate.exchange(requestUri, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<SearchResultItem> results = parseSearchResults(response.getBody());
                    if (results.isEmpty()) {
                        log.warn("TinyFish Search returned 0 results for query='{}'. Raw body preview: {}",
                                query, response.getBody().substring(0, Math.min(200, response.getBody().length())));
                    }
                    return results;
                } else {
                    log.warn("TinyFish Search non-2xx response: status={} for query='{}'", response.getStatusCode(), query);
                }
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("TinyFish Search rate limited (429) on attempt {}/{}. Retrying in {} ms...", attempt, maxRetries, backoffMs);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoffMs *= 2;
                }
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("TinyFish Search encountered error on attempt {}/{}: {}. Retrying in {} ms...",
                            attempt, maxRetries, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoffMs *= 2;
                } else {
                    log.error("Error executing TinyFish Search for query '{}' after {} attempts: {}", query, maxRetries, e.getMessage());
                }
            }
        }

        return List.of();
    }

    private List<SearchResultItem> parseSearchResults(String jsonBody) {
        List<SearchResultItem> items = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode resultsNode = root.get("results");
            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    String title = node.path("title").asText("");
                    String url = node.path("url").asText("");
                    String snippet = node.path("snippet").asText("");
                    String publisher = node.path("publisher").asText("");
                    String date = node.path("date").asText("");

                    if (!url.isBlank()) {
                        items.add(new SearchResultItem(title, url, snippet, publisher, date));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse TinyFish search response", e);
        }
        return items;
    }

    public static class SearchResultItem {
        private final String title;
        private final String url;
        private final String snippet;
        private final String publisher;
        private final String date;

        public SearchResultItem(String title, String url, String snippet, String publisher, String date) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
            this.publisher = publisher;
            this.date = date;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getSnippet() { return snippet; }
        public String getPublisher() { return publisher; }
        public String getDate() { return date; }
    }
}
