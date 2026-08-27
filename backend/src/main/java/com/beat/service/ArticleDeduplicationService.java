package com.beat.service;

import com.beat.dto.RawArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
public class ArticleDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(ArticleDeduplicationService.class);

    // Title cosine similarity threshold for semantic dedup
    private static final double TITLE_COSINE_SIMILARITY_THRESHOLD = 0.85;

    public List<RawArticle> deduplicate(List<RawArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        List<RawArticle> result = new ArrayList<>();
        Set<String> seenNormalizedUrls = new HashSet<>();

        for (RawArticle article : articles) {
            if (article == null || article.getUrl() == null || article.getUrl().isBlank()) {
                continue;
            }

            // Gate 1: Normalized URL dedup
            String normUrl = normalizeUrl(article.getUrl());
            if (seenNormalizedUrls.contains(normUrl)) {
                log.debug("Dropping duplicate URL: {}", article.getUrl());
                continue;
            }

            // Gate 2: Title cosine similarity > 0.85 check against already-accepted articles
            String normTitle = normalizeTitle(article.getTitle());
            if (!normTitle.isEmpty()) {
                boolean isDuplicateTitle = result.stream()
                        .filter(r -> r.getTitle() != null)
                        .anyMatch(r -> cosineSimilarity(article.getTitle(), r.getTitle()) > TITLE_COSINE_SIMILARITY_THRESHOLD);
                if (isDuplicateTitle) {
                    log.debug("Dropping duplicate title (cosine similarity > {}): {}", TITLE_COSINE_SIMILARITY_THRESHOLD, article.getTitle());
                    continue;
                }
            }

            seenNormalizedUrls.add(normUrl);
            result.add(article);
        }

        log.info("Deduplication completed: original size={}, deduplicated size={}", articles.size(), result.size());
        return result;
    }

    public String normalizeUrl(String rawUrl) {
        if (rawUrl == null) return "";
        try {
            URI uri = new URI(rawUrl.trim());
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath() : "";
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return host + path;
        } catch (Exception e) {
            return rawUrl.trim().toLowerCase();
        }
    }

    public String normalizeTitle(String title) {
        if (title == null) return "";
        // Remove non-alphanumeric characters, convert to lowercase, collapse whitespace
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public double cosineSimilarity(String a, String b) {
        String normA = normalizeTitle(a);
        String normB = normalizeTitle(b);
        if (normA.isEmpty() || normB.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> tfA = termFrequencies(normA);
        Map<String, Integer> tfB = termFrequencies(normB);

        double dotProduct = 0.0;
        for (Map.Entry<String, Integer> entry : tfA.entrySet()) {
            Integer countB = tfB.get(entry.getKey());
            if (countB != null) {
                dotProduct += entry.getValue() * countB;
            }
        }

        double normAVal = 0.0;
        for (int v : tfA.values()) {
            normAVal += (double) v * v;
        }
        double normBVal = 0.0;
        for (int v : tfB.values()) {
            normBVal += (double) v * v;
        }

        if (normAVal == 0.0 || normBVal == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normAVal) * Math.sqrt(normBVal));
    }

    private Map<String, Integer> termFrequencies(String normalized) {
        Map<String, Integer> freqs = new HashMap<>();
        String[] words = normalized.split("\\s+");
        for (String w : words) {
            if (!w.isBlank()) {
                freqs.put(w, freqs.getOrDefault(w, 0) + 1);
            }
        }
        return freqs;
    }

    // F1: Bigram helpers for Jaccard similarity

    private Set<String> bigrams(String normalized) {
        Set<String> bg = new HashSet<>();
        String[] words = normalized.split("\\s+");
        for (int i = 0; i < words.length - 1; i++) {
            bg.add(words[i] + "_" + words[i + 1]);
        }
        return bg;
    }

    double jaccardSimilarity(String a, String b) {
        Set<String> setA = bigrams(normalizeTitle(a));
        Set<String> setB = bigrams(normalizeTitle(b));
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;
        long inter = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - inter;
        return (double) inter / union;
    }
}
