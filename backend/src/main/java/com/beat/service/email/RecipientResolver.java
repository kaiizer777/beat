package com.beat.service.email;

import com.beat.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the destination email address for channels via DB query or default fallback.
 */
@Component
public class RecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(RecipientResolver.class);
    public static final String DEFAULT_FALLBACK_EMAIL = EmailConstants.DEFAULT_FALLBACK_RECIPIENT_EMAIL;

    private final JdbcTemplate jdbcTemplate;
    private final String fallbackRecipientEmail;

    @Autowired
    public RecipientResolver(JdbcTemplate jdbcTemplate,
                             @Value("${email.resend-target:${resend.recipient-email:" + EmailConstants.DEFAULT_FALLBACK_RECIPIENT_EMAIL + "}}") String fallbackRecipientEmail) {
        this.jdbcTemplate = jdbcTemplate;
        this.fallbackRecipientEmail = (fallbackRecipientEmail != null && !fallbackRecipientEmail.isBlank())
                ? fallbackRecipientEmail.trim()
                : DEFAULT_FALLBACK_EMAIL;
    }

    public RecipientResolver(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_FALLBACK_EMAIL);
    }

    /**
     * Resolves the destination email address for a channel by querying the user repository / table.
     */
    public String resolveRecipientEmail(Channel channel) {
        if (channel != null && channel.getUserId() != null && !channel.getUserId().isBlank() && jdbcTemplate != null) {
            try {
                List<String> emails = jdbcTemplate.query(
                        "SELECT email FROM \"User\" WHERE id = ?",
                        (rs, rowNum) -> rs.getString("email"),
                        channel.getUserId()
                );
                if (!emails.isEmpty() && emails.get(0) != null && !emails.get(0).isBlank()) {
                    String resolved = emails.get(0).trim();
                    log.info("Resolved target recipient email '{}' for channel '{}' from database (userId: '{}')",
                            resolved, channel.getName(), channel.getUserId());
                    return resolved;
                }
            } catch (Exception e) {
                log.warn("Failed to query user email for userId '{}' from User table: {}. Falling back to default recipient.",
                        channel.getUserId(), e.getMessage());
            }
        }
        return fallbackRecipientEmail;
    }

    public String getFallbackRecipientEmail() {
        return fallbackRecipientEmail;
    }
}
