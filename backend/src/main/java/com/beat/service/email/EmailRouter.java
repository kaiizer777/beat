package com.beat.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Routes email dispatches dynamically between Resend and Brevo based on recipient address.
 *
 * Routing Rules:
 * - resendTargetEmail (default: kaizerxdev@gmail.com) -> Resend (sandbox domain supported)
 * - All other recipients -> Brevo (production SMTP delivery)
 */
@Service
public class EmailRouter {

    private static final Logger log = LoggerFactory.getLogger(EmailRouter.class);
    public static final String DEFAULT_RESEND_TARGET_EMAIL = EmailConstants.DEFAULT_RESEND_TARGET_EMAIL;

    private final ResendEmailService resendEmailService;
    private final BrevoEmailService brevoEmailService;
    private final String resendTargetEmail;

    @Autowired
    public EmailRouter(ResendEmailService resendEmailService,
                       BrevoEmailService brevoEmailService,
                       @Value("${email.resend-target:" + EmailConstants.DEFAULT_RESEND_TARGET_EMAIL + "}") String resendTargetEmail) {
        this.resendEmailService = resendEmailService;
        this.brevoEmailService = brevoEmailService;
        this.resendTargetEmail = (resendTargetEmail != null && !resendTargetEmail.isBlank())
                ? resendTargetEmail.trim()
                : DEFAULT_RESEND_TARGET_EMAIL;
    }

    public EmailRouter(ResendEmailService resendEmailService,
                       BrevoEmailService brevoEmailService) {
        this(resendEmailService, brevoEmailService, DEFAULT_RESEND_TARGET_EMAIL);
    }

    /**
     * Resolves the appropriate EmailProvider strategy for the given recipient email.
     */
    public EmailProvider getProviderForRecipient(String recipientEmail) {
        if (isResendRecipient(recipientEmail)) {
            return resendEmailService;
        }
        return brevoEmailService;
    }

    /**
     * Routes and sends a generic HTML email to the target recipient.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlContent) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Target recipient email is null or blank. Dispatch aborted.");
            return false;
        }

        String normalizedEmail = toEmail.trim();
        EmailProvider provider = getProviderForRecipient(normalizedEmail);

        log.info("[EmailRouter] Sending via {} to {}", provider.getProviderName(), normalizedEmail);

        return provider.sendEmail(normalizedEmail, subject, htmlContent);
    }

    private boolean isResendRecipient(String email) {
        if (email == null) {
            return false;
        }
        return resendTargetEmail.equalsIgnoreCase(email.trim());
    }

    public String getResendTargetEmail() {
        return resendTargetEmail;
    }

    public ResendEmailService getResendEmailService() {
        return resendEmailService;
    }

    public BrevoEmailService getBrevoEmailService() {
        return brevoEmailService;
    }
}
