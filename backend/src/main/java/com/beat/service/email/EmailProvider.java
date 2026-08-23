package com.beat.service.email;

/**
 * Common contract for email delivery providers (Resend, Brevo, etc.).
 */
public interface EmailProvider {

    /**
     * Sends an HTML formatted email to the specified recipient.
     *
     * @param toEmail      Destination email address
     * @param subject      Subject of the email
     * @param htmlContent  HTML payload content
     * @return true if successfully dispatched (HTTP 2xx), false otherwise
     */
    boolean sendEmail(String toEmail, String subject, String htmlContent);

    /**
     * @return Human-readable provider identifier (e.g. "Resend", "Brevo")
     */
    String getProviderName();
}
