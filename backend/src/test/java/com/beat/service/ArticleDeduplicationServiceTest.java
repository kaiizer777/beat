package com.beat.service;

import com.beat.dto.RawArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArticleDeduplicationServiceTest {

    private ArticleDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        deduplicationService = new ArticleDeduplicationService();
    }

    @Test
    void deduplicate_dropsDuplicateNormalizedUrls() {
        RawArticle a1 = new RawArticle("Title 1", "https://example.com/news/story-1/", "snippet", "pub", "2026-02-01T00:00:00Z", null, null);
        RawArticle a2 = new RawArticle("Title 2", "https://example.com/news/story-1", "snippet", "pub", "2026-02-01T00:00:00Z", null, null);

        List<RawArticle> result = deduplicationService.deduplicate(List.of(a1, a2));
        assertEquals(1, result.size());
        assertEquals("Title 1", result.get(0).getTitle());
    }

    @Test
    void deduplicate_dropsNearDuplicateTitlesWithCosineSimilarityAboveThreshold() {
        // Very similar titles (cosine similarity > 0.85)
        RawArticle a1 = new RawArticle("OpenAI announces GPT 5 reasoning model release", "https://example.com/1", "snippet", "pub", null, null, null);
        RawArticle a2 = new RawArticle("OpenAI announces GPT 5 reasoning model release today", "https://example.com/2", "snippet", "pub", null, null, null);

        List<RawArticle> result = deduplicationService.deduplicate(List.of(a1, a2));
        assertEquals(1, result.size());
        assertEquals("OpenAI announces GPT 5 reasoning model release", result.get(0).getTitle());
    }

    @Test
    void deduplicate_keepsDistinctArticlesWithLowCosineSimilarity() {
        RawArticle a1 = new RawArticle("DeepSeek releases V3 open source model", "https://example.com/1", "snippet", "pub", null, null, null);
        RawArticle a2 = new RawArticle("OpenAI releases o3 reasoning benchmark results", "https://example.com/2", "snippet", "pub", null, null, null);

        List<RawArticle> result = deduplicationService.deduplicate(List.of(a1, a2));
        assertEquals(2, result.size());
    }

    @Test
    void cosineSimilarity_computesExpectedScores() {
        // Identical titles
        assertEquals(1.0, deduplicationService.cosineSimilarity("hello world", "hello world"), 0.001);

        // Completely disjoint titles
        assertEquals(0.0, deduplicationService.cosineSimilarity("apple banana", "car truck"), 0.001);

        // Near identical (>0.85)
        double simNear = deduplicationService.cosineSimilarity(
                "Anthropic launches Claude 3.7 Sonnet hybrid reasoning",
                "Anthropic launches Claude 3.7 Sonnet hybrid reasoning model"
        );
        assertTrue(simNear > 0.85, "Expected similarity > 0.85 but got " + simNear);

        // Distinct topic titles (<=0.85)
        double simDiff = deduplicationService.cosineSimilarity(
                "Anthropic launches Claude 3.7 Sonnet",
                "Google announces Gemini 2.0 Flash thinking model"
        );
        assertTrue(simDiff < 0.85, "Expected similarity < 0.85 but got " + simDiff);
    }
}
