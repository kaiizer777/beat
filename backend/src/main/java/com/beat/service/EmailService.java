package com.beat.service;

import com.beat.entity.Channel;
import com.beat.entity.DigestRun;
import com.beat.entity.NewsItem;
import com.beat.service.email.DigestHtmlTemplateBuilder;
import com.beat.service.email.EmailRouter;
import com.beat.service.email.RecipientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for building, formatting, and dispatching digest and authentication emails.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailRouter emailRouter;
    private final RecipientResolver recipientResolver;

    @Autowired
    public EmailService(EmailRouter emailRouter, RecipientResolver recipientResolver) {
        this.emailRouter = emailRouter;
        this.recipientResolver = recipientResolver != null
                ? recipientResolver
                : new RecipientResolver(null, RecipientResolver.DEFAULT_FALLBACK_EMAIL);
    }

    public EmailService(EmailRouter emailRouter) {
        this(emailRouter, new RecipientResolver(null, RecipientResolver.DEFAULT_FALLBACK_EMAIL));
    }

    /**
     * Constructs and sends a formatted digest email for the specified channel and run.
     */
    public boolean sendDigestEmail(Channel channel, DigestRun digestRun, List<NewsItem> newsItems) {
        if (newsItems == null || newsItems.isEmpty()) {
            log.info("No news items to send in digest email for Channel '{}'", channel != null ? channel.getName() : "Unknown");
            return false;
        }

        String recipientEmail = resolveRecipientEmail(channel);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("No recipient email found for channel '{}' (userId: '{}'). Skipping email dispatch.",
                    channel != null ? channel.getName() : "Unknown",
                    channel != null ? channel.getUserId() : "null");
            return false;
        }

        try {
            ZoneId zoneId = ZoneId.of("UTC");
            if (channel != null && channel.getTimezone() != null && !channel.getTimezone().isBlank()) {
                try {
                    zoneId = ZoneId.of(channel.getTimezone());
                } catch (Exception e) {
                    log.warn("Invalid timezone '{}' for channel '{}', defaulting to UTC", channel.getTimezone(), channel.getName());
                }
            }

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy").withZone(zoneId);
            Instant runTime = (digestRun != null && digestRun.getRunAt() != null) ? digestRun.getRunAt() : Instant.now();
            String formattedDate = dateFormatter.format(runTime);
            String channelName = channel != null && channel.getName() != null ? channel.getName() : "Daily Digest";
            String subject = "Beat Digest: " + channelName + " — " + formattedDate;

            String htmlBody = DigestHtmlTemplateBuilder.buildDigestHtml(channel, formattedDate, newsItems);

            return sendEmail(recipientEmail, subject, htmlBody);
        } catch (Exception e) {
            log.error("Error occurred while constructing/sending digest email for Channel '{}': {}",
                    channel != null ? channel.getName() : "Unknown", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Routes and sends a generic HTML email to the target recipient.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlContent) {
        return emailRouter.sendEmail(toEmail, subject, htmlContent);
    }

    /**
     * Sends an authentication/magic-link email using the appropriate provider.
     */
    public boolean sendAuthEmail(String toEmail, String magicLinkUrl) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Target auth email is null or blank. Dispatch aborted.");
            return false;
        }

        String subject = "Sign in to Beat";
        String htmlContent = DigestHtmlTemplateBuilder.buildMagicLinkHtml(magicLinkUrl);

        return sendEmail(toEmail, subject, htmlContent);
    }

    /**
     * Resolves the target recipient email address for a channel.
     */
    public String resolveRecipientEmail(Channel channel) {
        return recipientResolver.resolveRecipientEmail(channel);
    }

    /**
     * Helper to build HTML digest content directly.
     */
    public String buildHtmlDigest(Channel channel, String formattedDate, List<NewsItem> newsItems) {
        return DigestHtmlTemplateBuilder.buildDigestHtml(channel, formattedDate, newsItems);
    }

    public EmailRouter getEmailRouter() {
        return emailRouter;
    }

    public RecipientResolver getRecipientResolver() {
        return recipientResolver;
    }
}
