package com.beat.service;

import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.entity.NewsItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void sendDigestEmail_returnsFalse_whenApiKeyMissing() {
        ObjectMapper objectMapper = new ObjectMapper();
        EmailService emailService = new EmailService("", "test@example.com", "Beat <beat@example.com>", objectMapper);

        Channel channel = new Channel("test_user_id", "Tech News", "Tech", 10, java.time.LocalTime.of(8, 0), "UTC", true);
        DigestRun run = new DigestRun();
        NewsItem item = new NewsItem();
        item.setTitle("Test");

        boolean result = emailService.sendDigestEmail(channel, run, List.of(item));
        assertFalse(result, "Should return false when Resend API key is missing");
    }

    @Test
    void sendDigestEmail_returnsFalse_whenNewsItemsEmpty() {
        ObjectMapper objectMapper = new ObjectMapper();
        EmailService emailService = new EmailService("dummy_api_key", "test@example.com", "Beat <beat@example.com>", objectMapper);

        Channel channel = new Channel("test_user_id", "Tech News", "Tech", 10, java.time.LocalTime.of(8, 0), "UTC", true);
        DigestRun run = new DigestRun();

        boolean result = emailService.sendDigestEmail(channel, run, List.of());
        assertFalse(result, "Should return false when news items list is empty");
    }

    @Test
    void sendDigestEmail_resolvesUserEmailFromJdbcTemplate() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user_123")))
                .thenReturn(List.of("user123@example.com"));

        ObjectMapper objectMapper = new ObjectMapper();
        EmailService emailService = new EmailService("dummy_api_key", "fallback@example.com", "Beat <beat@example.com>", objectMapper, jdbcTemplate);

        Channel channel = new Channel("user_123", "AI Digest", "AI", 5, java.time.LocalTime.of(9, 0), "UTC", true);
        DigestRun run = new DigestRun();
        NewsItem item = new NewsItem();
        item.setTitle("AI Breakthrough");

        // Runs resolveRecipientEmail and queries jdbcTemplate
        emailService.sendDigestEmail(channel, run, List.of(item));
        Mockito.verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq("user_123"));
    }
}
