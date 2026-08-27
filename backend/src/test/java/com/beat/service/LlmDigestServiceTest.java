package com.beat.service;

import com.beat.dto.RawArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the post-fix fact-check behaviour: the catch block in
 * {@link LlmDigestService#verifyAndRefine} must fall back to the synthesized
 * blurb on a Groq transport failure rather than dropping the article. The
 * model-rejection path remains strict (see {@code testModelRejection_KeepsArticle}).
 */
@ExtendWith(MockitoExtension.class)
public class LlmDigestServiceTest {

    @Mock
    private GroqClient groqClient;

    private LlmDigestService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new LlmDigestService(groqClient, objectMapper);
    }

    @Test
    void verifyAndRefine_onGroqApiFailure_fallsBackToSynthesizedBlurb() throws Exception {
        // The original bug: a single Groq 429 / IOException caused the article
        // to be silently dropped, even though the synthesize stage had already
        // accepted the blurb.
        when(groqClient.generateJsonResponse(anyString(), anyString()))
                .thenThrow(new IOException("Groq API rate limit exceeded"));

        RawArticle article = new RawArticle(
                "Test Title", "https://example.com/x", "snippet text", "TestPub",
                "2026-08-12T00:00:00Z", "full text body", null);
        String originalBlurb = "This is the synthesized blurb the upstream stage produced.";

        LlmDigestService.VerificationResult result = service.verifyAndRefine(article, originalBlurb, Instant.now());

        assertTrue(result.isAccepted(),
                "On API failure, the article must be accepted (fallback to synthesized blurb), not rejected");
        assertEquals(originalBlurb, result.getRefinedBlurb(),
                "Fallback blurb must be the synthesized blurb the caller passed in");
        assertNull(result.getRejectionReason(),
                "No rejection reason should be reported on API failure — that's the whole point of the fix");
    }

    @Test
    void verifyAndRefine_onModelRejection_dropsArticle() throws Exception {
        // The model rejection path is still strict per workflow.md Phase 5.
        when(groqClient.generateJsonResponse(anyString(), anyString()))
                .thenReturn("{\"isValid\":false,\"refinedBlurb\":null,\"rejectionReason\":\"contains fabricated number\"}");

        RawArticle article = new RawArticle(
                "Test Title", "https://example.com/x", "snippet text", "TestPub",
                "2026-08-12T00:00:00Z", "full text body", null);

        LlmDigestService.VerificationResult result = service.verifyAndRefine(
                article, "some blurb", Instant.now());

        assertEquals(false, result.isAccepted(),
                "Real model rejection (isValid=false, no refined blurb) must still drop the article");
        assertEquals("contains fabricated number", result.getRejectionReason());
    }

    @Test
    void verifyAndRefine_onModelAccept_keepsArticle() throws Exception {
        when(groqClient.generateJsonResponse(anyString(), anyString()))
                .thenReturn("{\"isValid\":true,\"refinedBlurb\":null,\"rejectionReason\":null}");

        RawArticle article = new RawArticle(
                "Test Title", "https://example.com/x", "snippet text", "TestPub",
                "2026-08-12T00:00:00Z", "full text body", null);
        String blurb = "the original blurb";

        LlmDigestService.VerificationResult result = service.verifyAndRefine(article, blurb, Instant.now());

        assertTrue(result.isAccepted());
        assertEquals(blurb, result.getRefinedBlurb());
    }

    @Test
    void verifyAndRefine_onModelProvidesRefined_usesRefined() throws Exception {
        when(groqClient.generateJsonResponse(anyString(), anyString()))
                .thenReturn("{\"isValid\":false,\"refinedBlurb\":\"a corrected blurb\",\"rejectionReason\":\"x\"}");

        RawArticle article = new RawArticle(
                "Test Title", "https://example.com/x", "snippet text", "TestPub",
                "2026-08-12T00:00:00Z", "full text body", null);

        LlmDigestService.VerificationResult result = service.verifyAndRefine(article, "orig", Instant.now());

        assertTrue(result.isAccepted());
        assertEquals("a corrected blurb", result.getRefinedBlurb());
    }

    @Test
    void expandShortSnippet_producesCleanFactualFallbackWithoutFabricatedPhrases() {
        RawArticle article = new RawArticle(
                "OpenAI launches new model", "https://example.com/openai", "The company announced a breakthrough today",
                "Reuters", "2026-08-24T00:00:00Z", "Full text body", null);

        String fallback = service.expandShortSnippet(article.getSnippet(), article);

        assertEquals("The company announced a breakthrough today. Full reporting and continuing coverage are provided by Reuters.", fallback);
        assertTrue(!fallback.contains("active beat the digest tracks"), "Must not contain trigger phrase");
        assertTrue(!fallback.contains("worth a click-through"), "Must not contain promotional phrase");
    }

    @Test
    void synthesizeBlurbs_truncatesSourceTextAndSetsBlurbs() throws Exception {
        // L4: blurb must have >= 40 words and >= 2 sentences to avoid isShortBlurb triggering
        // the expand safety net. L5: synthesizeBlurbs now calls the 3-arg overload with maxTokens.
        String fullBlurb = "The new large language model represents a significant leap forward in natural language processing capabilities for enterprise applications. This development matters because it demonstrates how open-source research teams can compete with well-funded commercial labs on benchmark performance while maintaining transparent training methodologies and data practices.";
        when(groqClient.generateJsonResponse(anyString(), anyString(), anyInt()))
                .thenReturn("{\"blurbs\": [\"" + fullBlurb + "\"]}");

        String longContent = "A".repeat(1200);
        RawArticle article = new RawArticle(
                "Big AI News", "https://example.com/ai", "Short snippet", "TechCrunch",
                "2026-08-24T00:00:00Z", longContent, null);

        service.synthesizeBlurbs(List.of(article), "ai models", Instant.now());

        assertEquals(fullBlurb, article.getSummaryBlurb());
    }

    @Test
    void isShortBlurb_technicalTwoSentenceThirtyFiveWords_isValid() {
        // A 2-sentence 35-word technical blurb is valid and must NOT trigger expandShortBlurb
        String validBlurb = "OpenAI released a major architecture update that substantially lowers inference latency across large models. " +
                "This matters because enterprise teams can now run faster production pipelines with dramatically reduced computational overhead and latency.";
        String[] words = validBlurb.split("\\s+");
        assertTrue(words.length >= 25 && words.length <= 40, "Word count should be between 25 and 40: was " + words.length);

        assertFalse(service.isShortBlurb(validBlurb),
                "A 2-sentence 35-word technical blurb must not be flagged as short");
    }

    @Test
    void isShortBlurb_underTwentyFiveWords_isShort() {
        String shortBlurb = "New model released today. It is very fast.";
        assertTrue(service.isShortBlurb(shortBlurb),
                "Blurb under 25 words must be flagged as short");
    }

    @Test
    void batchVerifyAndRefine_skipsSnippetOnlyArticlesAndFactChecksFullText() throws Exception {
        // Article 1: full text available (> 500 chars) -> sent to Groq
        String fullText1 = "A".repeat(800);
        RawArticle article1 = new RawArticle(
                "Full Text Article", "https://example.com/1", "snippet 1", "Reuters",
                "2026-08-24T00:00:00Z", fullText1, "tinyfish");
        article1.setSummaryBlurb("Synthesized blurb for full text article 1.");

        // Article 2: snippet only (fullText null or < 400 chars) -> skipped, accepted as-is
        RawArticle article2 = new RawArticle(
                "Snippet Only Article", "https://example.com/2", "short snippet 2", "TechCrunch",
                "2026-08-24T00:00:00Z", null, null);
        article2.setSummaryBlurb("Synthesized blurb for snippet article 2.");

        // Article 3: full text available (> 500 chars) -> rejected by Groq
        String fullText3 = "B".repeat(900);
        RawArticle article3 = new RawArticle(
                "Rejected Full Article", "https://example.com/3", "snippet 3", "Bloomberg",
                "2026-08-24T00:00:00Z", fullText3, "jina");
        article3.setSummaryBlurb("Synthesized blurb for full text article 3.");

        // Groq only receives article 1 and article 3 (size 2)
        when(groqClient.generateJsonResponse(anyString(), anyString(), anyInt()))
                .thenReturn("{\"results\": [{\"isValid\": true, \"refinedBlurb\": null}, {\"isValid\": false, \"refinedBlurb\": null, \"rejectionReason\": \"hallucination\"}]}");

        List<RawArticle> result = service.batchVerifyAndRefine(List.of(article1, article2, article3), Instant.now());

        // Article 1 (valid) and Article 2 (snippet-only skip) should be accepted, Article 3 dropped
        assertEquals(2, result.size());
        assertTrue(result.contains(article1));
        assertTrue(result.contains(article2));
        assertFalse(result.contains(article3));
        assertEquals("Synthesized blurb for snippet article 2.", article2.getSummaryBlurb());
    }

    @Test
    void batchVerifyAndRefine_usesIncreasedMaxTokenCap() throws Exception {
        RawArticle article = new RawArticle(
                "Full Text Article", "https://example.com/1", "snippet 1", "Reuters",
                "2026-08-24T00:00:00Z", "C".repeat(600), "tinyfish");
        article.setSummaryBlurb("Synthesized blurb.");

        when(groqClient.generateJsonResponse(anyString(), anyString(), eq(800)))
                .thenReturn("{\"results\": [{\"isValid\": true, \"refinedBlurb\": null}]}");

        List<RawArticle> result = service.batchVerifyAndRefine(List.of(article), Instant.now());

        assertEquals(1, result.size());
        verify(groqClient).generateJsonResponse(anyString(), anyString(), eq(800));
    }
}
