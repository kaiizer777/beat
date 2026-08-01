package com.beat.service;

import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.entity.NewsItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
  * EmailService handles sending news digest emails via the Resend API.
  *
  * BREVO FALLBACK DOCUMENTATION (Phase 6.5):
  * If Resend domain verification becomes a blocker or limits are reached, Brevo offers 300 free emails/day.
  * To swap to Brevo:
  * 1. Change API endpoint to: POST https://api.brevo.com/v3/smtp/email
  * 2. Change Header from 'Authorization: Bearer <KEY>' to 'api-key: <BREVO_API_KEY>'
  * 3. Send JSON payload:
  *    {
  *      "sender": { "name": "Beat Digest", "email": "onboarding@resend.dev" },
  *      "to": [{ "email": "user@example.com" }],
  *      "subject": "Beat Digest: Channel Name",
  *      "htmlContent": "<html>...</html>"
  *    }
  */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String fallbackRecipientEmail;
    private final String fromEmail;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public EmailService(@Value("${resend.api-key:}") String apiKey,
                        @Value("${resend.recipient-email:kaizerxdev@gmail.com}") String fallbackRecipientEmail,
                        @Value("${resend.from-email:Beat Digest <onboarding@resend.dev>}") String fromEmail,
                        ObjectMapper objectMapper,
                        JdbcTemplate jdbcTemplate) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fallbackRecipientEmail = fallbackRecipientEmail != null ? fallbackRecipientEmail.trim() : "";
        this.fromEmail = fromEmail != null ? fromEmail.trim() : "";
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public EmailService(String apiKey, String fallbackRecipientEmail, String fromEmail, ObjectMapper objectMapper) {
        this(apiKey, fallbackRecipientEmail, fromEmail, objectMapper, null);
    }

    public boolean sendDigestEmail(Channel channel, DigestRun digestRun, List<NewsItem> newsItems) {
        if (apiKey.isEmpty()) {
            log.warn("Resend API key is missing. Skipping email dispatch for Channel '{}'", channel.getName());
            return false;
        }

        if (newsItems == null || newsItems.isEmpty()) {
            log.info("No news items to send in digest email for Channel '{}'", channel.getName());
            return false;
        }

        try {
            ZoneId zoneId = ZoneId.of("UTC");
            if (channel.getTimezone() != null && !channel.getTimezone().isBlank()) {
                try {
                    zoneId = ZoneId.of(channel.getTimezone());
                } catch (Exception e) {
                    log.warn("Invalid timezone '{}' for channel '{}', defaulting to UTC", channel.getTimezone(), channel.getName());
                }
            }

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy").withZone(zoneId);
            String formattedDate = dateFormatter.format(digestRun.getRunAt() != null ? digestRun.getRunAt() : java.time.Instant.now());
            String subject = "Beat Digest: " + channel.getName() + " — " + formattedDate;

            String htmlBody = buildHtmlDigest(channel, formattedDate, newsItems);

            String destinationEmail = resolveRecipientEmail(channel);
            if (destinationEmail == null || destinationEmail.isBlank()) {
                log.warn("No recipient email found for channel '{}' (userId: '{}'). Skipping email dispatch.", channel.getName(), channel.getUserId());
                return false;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", fromEmail);
            payload.put("to", List.of(destinationEmail));
            payload.put("subject", subject);
            payload.put("html", htmlBody);

            String requestJson = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            log.info("Sending digest email via Resend to '{}' for channel '{}' (userId: '{}')...", destinationEmail, channel.getName(), channel.getUserId());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully dispatched digest email for Channel '{}' to '{}'! Resend Response Code: {}", channel.getName(), destinationEmail, response.statusCode());
                return true;
            } else {
                log.error("Failed to send digest email via Resend. HTTP Status: {}, Body: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            log.error("Error occurred while constructing/sending digest email for Channel '{}': {}", channel.getName(), e.getMessage(), e);
            return false;
        }
    }

    private String resolveRecipientEmail(Channel channel) {
        if (channel != null && channel.getUserId() != null && !channel.getUserId().isBlank() && jdbcTemplate != null) {
            try {
                List<String> emails = jdbcTemplate.query(
                        "SELECT email FROM \"User\" WHERE id = ?",
                        (rs, rowNum) -> rs.getString("email"),
                        channel.getUserId()
                );
                if (!emails.isEmpty() && emails.get(0) != null && !emails.get(0).isBlank()) {
                    log.info("Resolved target recipient email '{}' for channel '{}' from database (userId: '{}')", emails.get(0), channel.getName(), channel.getUserId());
                    return emails.get(0).trim();
                }
            } catch (Exception e) {
                log.warn("Failed to query user email for userId '{}' from User table: {}. Falling back to default recipient.", channel.getUserId(), e.getMessage());
            }
        }
        return fallbackRecipientEmail;
    }

    private String buildHtmlDigest(Channel channel, String formattedDate, List<NewsItem> newsItems) {
        StringBuilder itemsHtml = new StringBuilder();

        for (NewsItem item : newsItems) {
            String sourceTag = (item.getSourceName() != null && !item.getSourceName().isBlank()) 
                    ? item.getSourceName() : "Web Source";
            String title = item.getTitle() != null ? escapeHtml(item.getTitle()) : "Untitled Article";
            String url = item.getUrl() != null ? item.getUrl() : "#";
            String blurb = item.getSummaryBlurb() != null ? escapeHtml(item.getSummaryBlurb()) : "";
            int rank = item.getRankPosition() != null ? item.getRankPosition() : 0;

            itemsHtml.append("""
                <div style="margin-bottom: 24px; padding: 20px; background-color: #ffffff; border-radius: 8px; border: 1px solid #e5e7eb; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
                    <div style="display: flex; align-items: center; margin-bottom: 8px;">
                        <span style="background-color: #3b82f6; color: #ffffff; font-size: 12px; font-weight: bold; padding: 2px 8px; border-radius: 12px; margin-right: 10px;">#%d</span>
                        <span style="color: #6b7280; font-size: 13px; font-weight: 500;">%s</span>
                    </div>
                    <h3 style="margin: 0 0 10px 0; font-size: 18px; font-weight: 600; line-height: 1.4;">
                        <a href="%s" target="_blank" style="color: #1d4ed8; text-decoration: none;">%s</a>
                    </h3>
                    <p style="margin: 0; color: #374151; font-size: 14px; line-height: 1.6; background-color: #f9fafb; padding: 12px; border-left: 3px solid #3b82f6; border-radius: 4px;">
                        %s
                    </p>
                </div>
            """.formatted(rank, escapeHtml(sourceTag), url, title, blurb));
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Beat Digest</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f3f4f6; margin: 0; padding: 20px; color: #111827;">
                <div style="max-width: 680px; margin: 0 auto; background-color: #f9fafb; border-radius: 12px; overflow: hidden; border: 1px solid #e5e7eb;">
                    <!-- Header -->
                    <div style="background-color: #1e293b; padding: 28px 24px; color: #ffffff;">
                        <span style="text-transform: uppercase; letter-spacing: 1px; font-size: 12px; color: #94a3b8; font-weight: 700;">BEAT RESEARCH DIGEST</span>
                        <h1 style="margin: 6px 0 4px 0; font-size: 24px; font-weight: 700; color: #f8fafc;">%s</h1>
                        <p style="margin: 0; font-size: 14px; color: #cbd5e1;">%s &bull; %d curated stories</p>
                    </div>

                    <!-- Main Content -->
                    <div style="padding: 24px;">
                        %s
                    </div>

                    <!-- Footer -->
                    <div style="background-color: #e2e8f0; padding: 16px 24px; text-align: center; font-size: 12px; color: #64748b;">
                        <p style="margin: 0;">Automated Multi-Cron Research Digest generated by <strong>Beat</strong></p>
                    </div>
                </div>
            </body>
            </html>
        """.formatted(escapeHtml(channel.getName()), escapeHtml(formattedDate), newsItems.size(), itemsHtml.toString());
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
