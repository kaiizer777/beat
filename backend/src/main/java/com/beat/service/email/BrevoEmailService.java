package com.beat.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Primary
@Service
public class BrevoEmailService implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final Pattern SENDER_PATTERN = Pattern.compile("^(.*?)\\s*<(.+?)>$");

    private final String apiKey;
    private final String fromEmail;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public BrevoEmailService(@Value("${brevo.api-key:}") String apiKey,
                             @Value("${brevo.from-email:Beat Digest <kanekigaminz@gmail.com>}") String fromEmail,
                             ObjectMapper objectMapper) {
        this(apiKey, fromEmail, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public BrevoEmailService(String apiKey,
                             String fromEmail,
                             ObjectMapper objectMapper,
                             HttpClient httpClient) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromEmail = fromEmail != null ? fromEmail.trim() : "Beat Digest <kanekigaminz@gmail.com>";
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean sendEmail(String toEmail, String subject, String htmlContent) {
        if (apiKey.isEmpty()) {
            log.warn("Brevo API key is missing. Skipping email dispatch to '{}'", toEmail);
            return false;
        }

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Recipient email is empty. Skipping Brevo dispatch.");
            return false;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sender", parseSender(fromEmail));
            payload.put("to", List.of(Map.of("email", toEmail.trim())));
            payload.put("subject", subject != null ? subject : "Beat Digest");
            payload.put("htmlContent", htmlContent != null ? htmlContent : "");

            String requestJson = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully dispatched email via Brevo to '{}'! Response Code: {}", toEmail, response.statusCode());
                return true;
            } else {
                log.error("Failed to send email via Brevo to '{}'. HTTP Status: {}, Body: {}", toEmail, response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Error occurred while sending email via Brevo to '{}': {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Brevo";
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * Parses fromEmail string such as "Beat Digest <noreply@brevo.com>" into { name: "Beat Digest", email: "noreply@brevo.com" }.
     */
    Map<String, String> parseSender(String rawSender) {
        Map<String, String> sender = new HashMap<>();
        if (rawSender == null || rawSender.isBlank()) {
            sender.put("name", "Beat Digest");
            sender.put("email", "noreply@brevo.com");
            return sender;
        }

        Matcher matcher = SENDER_PATTERN.matcher(rawSender.trim());
        if (matcher.matches()) {
            String name = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            sender.put("name", !name.isEmpty() ? name : "Beat Digest");
            sender.put("email", !email.isEmpty() ? email : "noreply@brevo.com");
        } else {
            sender.put("name", "Beat Digest");
            sender.put("email", rawSender.trim());
        }
        return sender;
    }
}
