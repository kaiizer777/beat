package com.beat.service.email;

/**
 * Shared constants for email delivery and recipient resolution.
 */
public final class EmailConstants {

    private EmailConstants() {
        // Utility / Constants class
    }

    public static final String DEFAULT_TARGET_EMAIL = "kaizerxdev@gmail.com";
    public static final String DEFAULT_RESEND_TARGET_EMAIL = DEFAULT_TARGET_EMAIL;
    public static final String DEFAULT_FALLBACK_RECIPIENT_EMAIL = DEFAULT_TARGET_EMAIL;
}
