package com.beat.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String fromEmail;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ResendEmailService(@Value("${resend.api-key:}") String apiKey,
                              @Value("${resend.from-email:Beat Digest <onboarding@resend.dev>}") String fromEmail,
                              ObjectMapper objectMapper) {
        this(apiKey, fromEmail, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public ResendEmailService(String apiKey,
                              String fromEmail,
                              ObjectMapper objectMapper,
                              HttpClient httpClient) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromEmail = fromEmail != null ? fromEmail.trim() : "Beat Digest <onboarding@resend.dev>";
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean sendEmail(String toEmail, String subject, String htmlContent) {
        if (apiKey.isEmpty()) {
            log.warn("Resend API key is missing. Skipping email dispatch to '{}'", toEmail);
            return false;
        }

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Recipient email is empty. Skipping Resend dispatch.");
            return false;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", fromEmail);
            payload.put("to", List.of(toEmail.trim()));
            payload.put("subject", subject != null ? subject : "Beat Digest");
            payload.put("html", htmlContent != null ? htmlContent : "");

            String requestJson = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully dispatched email via Resend to '{}'! Response Code: {}", toEmail, response.statusCode());
                return true;
            } else {
                log.error("Failed to send email via Resend to '{}'. HTTP Status: {}, Body: {}", toEmail, response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Error occurred while sending email via Resend to '{}': {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Resend";
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getApiKey() {
        return apiKey;
    }
}
