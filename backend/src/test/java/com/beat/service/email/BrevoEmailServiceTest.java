package com.beat.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrevoEmailServiceTest {

    private ObjectMapper objectMapper;
    private HttpClient mockHttpClient;
    private HttpResponse<String> mockHttpResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockHttpClient = mock(HttpClient.class);
        mockHttpResponse = mock(HttpResponse.class);
    }

    @Test
    void sendEmail_returnsFalse_whenApiKeyMissing() {
        BrevoEmailService service = new BrevoEmailService("", "Beat Digest <noreply@brevo.com>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("user@example.com", "Test Subject", "<p>Hello</p>");

        assertFalse(result);
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void sendEmail_returnsFalse_whenRecipientIsBlank() {
        BrevoEmailService service = new BrevoEmailService("xkeysib-123", "Beat Digest <noreply@brevo.com>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("   ", "Test Subject", "<p>Hello</p>");

        assertFalse(result);
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void sendEmail_success_sendsCorrectHeadersAndPayload() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(201);
        when(mockHttpResponse.body()).thenReturn("{\"messageId\":\"<20260824@smtp-relay.mailin.fr>\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        BrevoEmailService service = new BrevoEmailService("xkeysib-test-api-key", "Beat News <news@beat.com>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("recipient@domain.com", "Custom Digest", "<h1>Digest Content</h1>");

        assertTrue(result);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = captor.getValue();
        assertEquals("https://api.brevo.com/v3/smtp/email", sentRequest.uri().toString());
        assertEquals("POST", sentRequest.method());
        assertEquals("xkeysib-test-api-key", sentRequest.headers().firstValue("api-key").orElse(""));
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void sendEmail_returnsFalse_whenBrevoReturnsError() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(400);
        when(mockHttpResponse.body()).thenReturn("{\"code\":\"invalid_parameter\",\"message\":\"Invalid email\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        BrevoEmailService service = new BrevoEmailService("xkeysib-test-key", "Beat Digest <noreply@brevo.com>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("bad-email", "Subject", "<p>Content</p>");

        assertFalse(result);
    }

    @Test
    void parseSender_correctlyExtractsNameAndEmail() {
        BrevoEmailService service = new BrevoEmailService("key", "Beat Digest <noreply@brevo.com>", objectMapper, mockHttpClient);

        Map<String, String> sender = service.parseSender("Beat Digest <noreply@brevo.com>");
        assertEquals("Beat Digest", sender.get("name"));
        assertEquals("noreply@brevo.com", sender.get("email"));

        Map<String, String> plainSender = service.parseSender("simple@example.com");
        assertEquals("Beat Digest", plainSender.get("name"));
        assertEquals("simple@example.com", plainSender.get("email"));

        Map<String, String> nullSender = service.parseSender(null);
        assertEquals("Beat Digest", nullSender.get("name"));
        assertEquals("noreply@brevo.com", nullSender.get("email"));
    }

    @Test
    void getProviderName_returnsBrevo() {
        BrevoEmailService service = new BrevoEmailService("key", "from@example.com", objectMapper, mockHttpClient);
        assertEquals("Brevo", service.getProviderName());
    }
}
