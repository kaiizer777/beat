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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
}
