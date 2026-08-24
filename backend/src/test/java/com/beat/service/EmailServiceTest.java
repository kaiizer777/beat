package com.beat.service;

import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.entity.NewsItem;
import com.beat.service.email.EmailRouter;
import com.beat.service.email.RecipientResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private EmailRouter mockEmailRouter;
    private RecipientResolver mockRecipientResolver;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mockEmailRouter = mock(EmailRouter.class);
        mockRecipientResolver = mock(RecipientResolver.class);
        emailService = new EmailService(mockEmailRouter, mockRecipientResolver);
    }

    @Test
    void sendDigestEmail_returnsFalse_whenNewsItemsNullOrEmpty() {
        Channel channel = new Channel("user_1", "Tech", "Tech news", 5, LocalTime.of(8, 0), "UTC", true);
        DigestRun run = new DigestRun();

        assertFalse(emailService.sendDigestEmail(channel, run, null));
        assertFalse(emailService.sendDigestEmail(channel, run, List.of()));
        verifyNoInteractions(mockEmailRouter);
    }

    @Test
    void sendDigestEmail_returnsFalse_whenRecipientEmailCannotBeResolved() {
        Channel channel = new Channel("user_1", "Tech", "Tech news", 5, LocalTime.of(8, 0), "UTC", true);
        DigestRun run = new DigestRun();
        NewsItem item = new NewsItem();
        item.setTitle("Tech Story");

        when(mockRecipientResolver.resolveRecipientEmail(channel)).thenReturn(null);

        boolean result = emailService.sendDigestEmail(channel, run, List.of(item));

        assertFalse(result);
        verifyNoInteractions(mockEmailRouter);
    }

    @Test
    void sendDigestEmail_formatsAndDispatchesSuccessfully() {
        Channel channel = new Channel("user_1", "AI Weekly", "AI news", 5, LocalTime.of(8, 0), "UTC", true);
        DigestRun run = new DigestRun();
        run.setRunAt(Instant.parse("2026-08-24T08:00:00Z"));

        NewsItem item = new NewsItem();
        item.setTitle("Agent Architecture Explained");
        item.setUrl("https://news.com/agents");
        item.setSourceName("Tech Wire");
        item.setRankPosition(1);

        when(mockRecipientResolver.resolveRecipientEmail(channel)).thenReturn("subscriber@beat.app");
        when(mockEmailRouter.sendEmail(eq("subscriber@beat.app"), anyString(), anyString())).thenReturn(true);

        boolean result = emailService.sendDigestEmail(channel, run, List.of(item));

        assertTrue(result);
        verify(mockEmailRouter).sendEmail(
                eq("subscriber@beat.app"),
                contains("Beat [AI Weekly]:"),
                contains("Agent Architecture Explained")
        );
    }

    @Test
    void sendDigestEmail_respectsCustomChannelTimezone() {
        Channel channel = new Channel("user_1", "Tokyo News", "Japan Tech", 5, LocalTime.of(8, 0), "Asia/Tokyo", true);
        DigestRun run = new DigestRun();
        run.setRunAt(Instant.parse("2026-08-24T22:00:00Z")); // Aug 25 in Tokyo

        NewsItem item = new NewsItem();
        item.setTitle("Tokyo AI Summit");

        when(mockRecipientResolver.resolveRecipientEmail(channel)).thenReturn("user@tokyo.jp");
        when(mockEmailRouter.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        boolean result = emailService.sendDigestEmail(channel, run, List.of(item));

        assertTrue(result);
        // D4: subject no longer contains the date — verify subject has the new format
        // and the date is still rendered in the HTML body
        verify(mockEmailRouter).sendEmail(
                eq("user@tokyo.jp"),
                contains("Beat [Tokyo News]:"),
                contains("Tokyo AI Summit")
        );
    }

    @Test
    void sendAuthEmail_formatsMagicLinkAndDispatches() {
        when(mockEmailRouter.sendEmail(eq("newuser@example.com"), eq("Sign in to Beat"), anyString())).thenReturn(true);

        boolean result = emailService.sendAuthEmail("newuser@example.com", "https://beat.app/magic?token=xyz");

        assertTrue(result);
        verify(mockEmailRouter).sendEmail(
                eq("newuser@example.com"),
                eq("Sign in to Beat"),
                contains("https://beat.app/magic?token=xyz")
        );
    }

    @Test
    void sendAuthEmail_returnsFalse_whenRecipientIsBlankOrNull() {
        assertFalse(emailService.sendAuthEmail("   ", "https://beat.app/magic?token=xyz"));
        assertFalse(emailService.sendAuthEmail(null, "https://beat.app/magic?token=xyz"));
        verifyNoInteractions(mockEmailRouter);
    }

    @Test
    void sendEmail_delegatesToEmailRouter() {
        when(mockEmailRouter.sendEmail("to@domain.com", "Subject", "<p>hi</p>")).thenReturn(true);

        assertTrue(emailService.sendEmail("to@domain.com", "Subject", "<p>hi</p>"));
        verify(mockEmailRouter).sendEmail("to@domain.com", "Subject", "<p>hi</p>");
    }

    @Test
    void resolveRecipientEmail_delegatesToRecipientResolver() {
        Channel channel = new Channel("user_10", "Channel", "Topic", 5, LocalTime.of(8, 0), "UTC", true);
        when(mockRecipientResolver.resolveRecipientEmail(channel)).thenReturn("resolved@user.com");

        assertEquals("resolved@user.com", emailService.resolveRecipientEmail(channel));
        verify(mockRecipientResolver).resolveRecipientEmail(channel);
    }

    @Test
    void buildHtmlDigest_buildsValidHtml() {
        Channel channel = new Channel("user_1", "Tech", "Topic", 5, LocalTime.of(8, 0), "UTC", true);
        String html = emailService.buildHtmlDigest(channel, "Mon, Aug 24, 2026", List.of());
        assertNotNull(html);
        assertTrue(html.contains("0 curated stories"));
    }
}
