package com.beat.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResendEmailServiceTest {

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
        ResendEmailService service = new ResendEmailService("", "Beat <onboarding@resend.dev>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("kaizerxdev@gmail.com", "Test Subject", "<p>Hello</p>");

        assertFalse(result);
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void sendEmail_returnsFalse_whenRecipientIsBlank() {
        ResendEmailService service = new ResendEmailService("re_123", "Beat <onboarding@resend.dev>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("   ", "Test Subject", "<p>Hello</p>");

        assertFalse(result);
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void sendEmail_success_sendsCorrectRequest() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn("{\"id\":\"msg_123\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        ResendEmailService service = new ResendEmailService("re_test_key", "Beat <onboarding@resend.dev>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("kaizerxdev@gmail.com", "Test Digest", "<h1>Digest</h1>");

        assertTrue(result);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = captor.getValue();
        assertEquals("https://api.resend.com/emails", sentRequest.uri().toString());
        assertEquals("POST", sentRequest.method());
        assertEquals("Bearer re_test_key", sentRequest.headers().firstValue("Authorization").orElse(""));
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void sendEmail_returnsFalse_whenResendReturnsHttpError() throws IOException, InterruptedException {
        when(mockHttpResponse.statusCode()).thenReturn(403);
        when(mockHttpResponse.body()).thenReturn("{\"statusCode\":403,\"message\":\"Domain not verified\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        ResendEmailService service = new ResendEmailService("re_test_key", "Beat <onboarding@resend.dev>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("other@example.com", "Test Subject", "<p>Content</p>");

        assertFalse(result);
    }

    @Test
    void sendEmail_returnsFalse_whenHttpThrowsException() throws IOException, InterruptedException {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection timed out"));

        ResendEmailService service = new ResendEmailService("re_test_key", "Beat <onboarding@resend.dev>", objectMapper, mockHttpClient);
        boolean result = service.sendEmail("kaizerxdev@gmail.com", "Test Subject", "<p>Content</p>");

        assertFalse(result);
    }

    @Test
    void getProviderName_returnsResend() {
        ResendEmailService service = new ResendEmailService("re_key", "from@example.com", objectMapper, mockHttpClient);
        assertEquals("Resend", service.getProviderName());
    }
}
