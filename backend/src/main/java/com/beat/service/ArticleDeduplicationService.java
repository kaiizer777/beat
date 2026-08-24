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

    // F1: Jaccard bigram similarity threshold for semantic dedup
    private static final double SIMILARITY_THRESHOLD = 0.55;

    public List<RawArticle> deduplicate(List<RawArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        List<RawArticle> result = new ArrayList<>();
        Set<String> seenNormalizedUrls = new HashSet<>();
        Set<String> seenNormalizedTitles = new HashSet<>();

        for (RawArticle article : articles) {
            if (article == null || article.getUrl() == null || article.getUrl().isBlank()) {
                continue;
            }

            // Gate 1: URL dedup
            String normUrl = normalizeUrl(article.getUrl());
            if (seenNormalizedUrls.contains(normUrl)) {
                log.debug("Dropping duplicate URL: {}", article.getUrl());
                continue;
            }

            // Gate 2: Exact title dedup
            String normTitle = normalizeTitle(article.getTitle());
            if (!normTitle.isEmpty() && seenNormalizedTitles.contains(normTitle)) {
                log.debug("Dropping duplicate title: {}", article.getTitle());
                continue;
            }

            // Gate 3: F1 — Jaccard bigram similarity against already-accepted articles
            if (!normTitle.isEmpty()) {
                boolean isSemDup = result.stream()
                        .filter(r -> r.getTitle() != null)
                        .anyMatch(r -> jaccardSimilarity(article.getTitle(), r.getTitle()) > SIMILARITY_THRESHOLD);
                if (isSemDup) {
                    log.debug("Dropping semantic duplicate (Jaccard): {}", article.getTitle());
                    continue;
                }
            }

            seenNormalizedUrls.add(normUrl);
            if (!normTitle.isEmpty()) {
                seenNormalizedTitles.add(normTitle);
            }
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
