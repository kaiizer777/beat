package com.beat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GroqClientTest {

    @Mock
    private GroqUsageTracker usageTracker;

    private GroqClient groqClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        groqClient = new GroqClient("test-key", "openai/gpt-oss-20b", objectMapper, usageTracker);
    }

    @Test
    void extractRetryDelayMs_parsesRetryAfterHeader() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        HttpHeaders headers = HttpHeaders.of(Map.of("retry-after", List.of("28")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        long delayMs = groqClient.extractRetryDelayMs(response, 5000);

        // 28s + 500ms safety buffer = 28500ms
        assertEquals(28500, delayMs);
    }

    @Test
    void extractRetryDelayMs_parsesResetTokensHeader() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        HttpHeaders headers = HttpHeaders.of(Map.of("x-ratelimit-reset-tokens", List.of("28.35s")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        long delayMs = groqClient.extractRetryDelayMs(response, 5000);

        // 28.35s = 28350ms + 500ms safety buffer = 28850ms
        assertEquals(28850, delayMs);
    }

    @Test
    void extractRetryDelayMs_parsesResetTokensHeaderWithMs() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        HttpHeaders headers = HttpHeaders.of(Map.of("x-ratelimit-reset-tokens", List.of("1200ms")), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        long delayMs = groqClient.extractRetryDelayMs(response, 5000);

        // 1200ms + 500ms buffer = 1700ms
        assertEquals(1700, delayMs);
    }

    @Test
    void extractRetryDelayMs_parsesBodyJsonErrorMessage() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        HttpHeaders headers = HttpHeaders.of(Map.of(), (k, v) -> true);
        when(response.headers()).thenReturn(headers);
        when(response.body()).thenReturn("""
                {
                  "error": {
                    "message": "Rate limit reached for model openai/gpt-oss-20b on tokens per minute (TPM): Limit 8000, Used 5800, Requested 2800. Please try again in 28.35s.",
                    "type": "tokens",
                    "code": "rate_limit_exceeded"
                  }
                }
                """);

        long delayMs = groqClient.extractRetryDelayMs(response, 5000);

        // 28.35s -> 28350 + 500 = 28850ms
        assertEquals(28850, delayMs);
    }

    @Test
    void extractRetryDelayMs_fallsBackToDefaultWhenNoMatch() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        HttpHeaders headers = HttpHeaders.of(Map.of(), (k, v) -> true);
        when(response.headers()).thenReturn(headers);
        when(response.body()).thenReturn("{\"error\": \"Internal server error\"}");

        long delayMs = groqClient.extractRetryDelayMs(response, 5000);

        assertEquals(5000, delayMs);
    }
}
