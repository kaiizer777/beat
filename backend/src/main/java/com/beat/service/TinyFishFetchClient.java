package com.beat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TinyFishFetchClient {

    private static final Logger log = LoggerFactory.getLogger(TinyFishFetchClient.class);
    private static final String FETCH_API_URL = "https://api.fetch.tinyfish.ai";
    private static final String JINA_READER_PREFIX = "https://r.jina.ai/";

    // E4: Minimum usable content length and paywall signal detection
    private static final int MIN_CONTENT_CHARS = 500;
    private static final List<String> PAYWALL_SIGNALS = List.of(
        "subscribe to read", "sign in to continue", "create a free account",
        "this article is for subscribers", "paywall", "please log in",
        "register to access", "become a member", "subscription required"
    );

    private static final Pattern HTML_COMMENT_PATTERN =
            Pattern.compile("<!--[\\s\\S]*?-->");
    private static final Pattern SCRIPT_STYLE_PATTERN =
            Pattern.compile("(?is)<(script|style|svg|noscript|header|footer|nav|aside)[^>]*>.*?</\\1>");
    private static final Pattern ARTICLE_TAG_PATTERN =
            Pattern.compile("(?is)<article[^>]*>(.*?)</article>");
    private static final Pattern MAIN_TAG_PATTERN =
            Pattern.compile("(?is)<main[^>]*>(.*?)</main>");
    private static final Pattern BLOCK_TAGS_PATTERN =
            Pattern.compile("(?i)<(?:p|div|br|h[1-6]|li|tr|blockquote|section)[^>]*>");
    private static final Pattern REMAINING_TAGS_PATTERN =
            Pattern.compile("<[^>]+>");

    private boolean isUsableContent(String content) {
        if (content == null) {
            return false;
        }
        String trimmed = content.trim();
        if (trimmed.length() <= 200) {
            return false;
        }

        String lower = trimmed.toLowerCase(Locale.ENGLISH);
        if (PAYWALL_SIGNALS.stream().anyMatch(lower::contains)) {
            return false;
        }

        if (trimmed.length() >= MIN_CONTENT_CHARS) {
            return true;
        }

        // Short content fallback: 200 < len < 500 characters, must have >= 2 sentences
        int sentenceCount = countSentences(trimmed);
        if (sentenceCount >= 2) {
            log.warn("Accepting short content ({} chars, {} sentences) to prevent pipeline starvation",
                    trimmed.length(), sentenceCount);
            return true;
        }

        return false;
    }

    private int countSentences(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] parts = text.split("[.!?](\\s+|$)");
        int count = 0;
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String jinaApiKey;

    @Autowired
    public TinyFishFetchClient(
            @Value("${tinyfish.api-key:${TINYFISH_API_KEY:}}") String apiKey,
            @Value("${jina.api-key:${JINA_API_KEY:}}") String jinaApiKey) {
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
        this.jinaApiKey = jinaApiKey;
    }

    public TinyFishFetchClient(String apiKey) {
        this(apiKey, "");
    }

    public FetchResult fetchContent(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        // Try TinyFish Fetch first
        FetchResult result = tryTinyFishFetch(url);
        if (result != null && isUsableContent(result.getContent())) {
            return result;
        }

        // Fallback to Jina AI Reader
        log.info("TinyFish fetch unavailable or returned low content for URL: {}. Retrying with Jina AI Reader...", url);
        FetchResult jinaResult = tryJinaFetch(url);
        if (jinaResult != null && isUsableContent(jinaResult.getContent())) {
            return jinaResult;
        }

        // Fallback to Defuddle / Readability scraper if Jina returns <500 chars, 403, or fails
        log.info("Jina fetch unavailable or returned low content for URL: {}. Retrying with Defuddle fallback...", url);
        FetchResult defuddleResult = tryDefuddleFetch(url);
        if (defuddleResult != null && isUsableContent(defuddleResult.getContent())) {
            return defuddleResult;
        }

        log.warn("All fetch mechanisms (TinyFish, Jina, Defuddle) failed to extract content for URL: {}", url);
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
                        if (firstResult.has("text") || firstResult.has("content")) {
                            String content = firstResult.has("text")
                                    ? firstResult.path("text").asText("")
                                    : firstResult.path("content").asText("");
                            if (content.isBlank() && firstResult.has("content")) {
                                content = firstResult.path("content").asText("");
                            }
                            if (!content.isBlank()) {
                                return new FetchResult(content, "tinyfish");
                            }
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
                if (jinaApiKey != null && !jinaApiKey.trim().isEmpty()) {
                    headers.set("Authorization", "Bearer " + jinaApiKey.trim());
                    headers.set("X-Retrieve-Source", "true");
                } else {
                    log.debug("Jina anonymous mode - may 403 on Render");
                }
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isBlank()) {
                    return new FetchResult(response.getBody(), "jina");
                }
            } catch (Exception e) {
                long backoffMs = 500L;
                if (e instanceof HttpStatusCodeException hsce) {
                    int code = hsce.getStatusCode().value();
                    if (code == 403 || code == 429) {
                        backoffMs = 2000L;
                    }
                } else if (e.getMessage() != null && (e.getMessage().contains("403") || e.getMessage().contains("429"))) {
                    backoffMs = 2000L;
                }

                if (attempt < maxAttempts) {
                    log.warn("Jina AI Reader error on attempt {}/{} for {}: {}. Retrying with {}ms backoff...",
                            attempt, maxAttempts, url, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.warn("Jina AI Reader failed for {} after {} attempts: {}", url, maxAttempts, e.getMessage());
                }
            }
        }
        return null;
    }

    public FetchResult tryDefuddleFetch(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isBlank()) {
                String cleaned = extractReadableText(response.getBody());
                if (cleaned != null && !cleaned.isBlank()) {
                    log.info("Defuddle fallback fetch succeeded for URL: {} ({} chars extracted)", url, cleaned.length());
                    return new FetchResult(cleaned, "defuddle");
                }
            }
        } catch (Exception e) {
            log.warn("Defuddle fallback fetch failed for URL {}: {}", url, e.getMessage());
        }
        return null;
    }

    private String extractReadableText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html;
        text = HTML_COMMENT_PATTERN.matcher(text).replaceAll("");
        text = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll(" ");

        Matcher articleMatcher = ARTICLE_TAG_PATTERN.matcher(text);
        if (articleMatcher.find()) {
            String articleContent = articleMatcher.group(1);
            if (articleContent != null && articleContent.length() > 200) {
                text = articleContent;
            }
        } else {
            Matcher mainMatcher = MAIN_TAG_PATTERN.matcher(text);
            if (mainMatcher.find()) {
                String mainContent = mainMatcher.group(1);
                if (mainContent != null && mainContent.length() > 200) {
                    text = mainContent;
                }
            }
        }

        text = BLOCK_TAGS_PATTERN.matcher(text).replaceAll("\n");
        text = REMAINING_TAGS_PATTERN.matcher(text).replaceAll(" ");
        text = HtmlUtils.htmlUnescape(text);
        text = text.replaceAll("[ \\t\\x0B\\f]+", " ");
        text = text.replaceAll("(\\r?\\n\\s*){2,}", "\n\n");
        return text.trim();
    }

    public static class FetchResult {
        private final String content;
        private final String source; // "tinyfish", "jina", or "defuddle"

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
