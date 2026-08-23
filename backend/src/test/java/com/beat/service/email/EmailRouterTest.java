package com.beat.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailRouterTest {

    private ResendEmailService mockResend;
    private BrevoEmailService mockBrevo;
    private EmailRouter router;

    @BeforeEach
    void setUp() {
        mockResend = mock(ResendEmailService.class);
        mockBrevo = mock(BrevoEmailService.class);

        when(mockResend.getProviderName()).thenReturn("Resend");
        when(mockBrevo.getProviderName()).thenReturn("Brevo");

        router = new EmailRouter(mockResend, mockBrevo, "kaizerxdev@gmail.com");
    }

    @Test
    void getProviderForRecipient_routesToResend_forDefaultTargetEmail() {
        assertEquals(mockResend, router.getProviderForRecipient("kaizerxdev@gmail.com"));
        assertEquals(mockResend, router.getProviderForRecipient("KAIZERXDEV@GMAIL.COM"));
        assertEquals(mockResend, router.getProviderForRecipient("  kaizerxdev@gmail.com  "));
    }

    @Test
    void getProviderForRecipient_routesToBrevo_forOtherEmails() {
        assertEquals(mockBrevo, router.getProviderForRecipient("user@example.com"));
        assertEquals(mockBrevo, router.getProviderForRecipient("alice@gmail.com"));
        assertEquals(mockBrevo, router.getProviderForRecipient("bob@corporate.org"));
    }

    @Test
    void getProviderForRecipient_respectsConfigurableTargetEmail() {
        EmailRouter customRouter = new EmailRouter(mockResend, mockBrevo, "custom.dev@startup.io");

        assertEquals(mockResend, customRouter.getProviderForRecipient("custom.dev@startup.io"));
        assertEquals(mockResend, customRouter.getProviderForRecipient("CUSTOM.DEV@STARTUP.IO"));
        assertEquals(mockBrevo, customRouter.getProviderForRecipient("kaizerxdev@gmail.com"));
    }

    @Test
    void sendEmail_routesToResend_whenRecipientIsTarget() {
        when(mockResend.sendEmail(eq("kaizerxdev@gmail.com"), anyString(), anyString())).thenReturn(true);

        boolean result = router.sendEmail("kaizerxdev@gmail.com", "Subject", "<h1>Hello</h1>");

        assertTrue(result);
        verify(mockResend).sendEmail(eq("kaizerxdev@gmail.com"), eq("Subject"), eq("<h1>Hello</h1>"));
        verifyNoInteractions(mockBrevo);
    }

    @Test
    void sendEmail_routesToBrevo_whenRecipientIsOther() {
        when(mockBrevo.sendEmail(eq("someone@domain.com"), anyString(), anyString())).thenReturn(true);

        boolean result = router.sendEmail("someone@domain.com", "Subject", "<h1>Hello</h1>");

        assertTrue(result);
        verify(mockBrevo).sendEmail(eq("someone@domain.com"), eq("Subject"), eq("<h1>Hello</h1>"));
        verifyNoInteractions(mockResend);
    }

    @Test
    void sendEmail_returnsFalse_whenRecipientIsBlankOrNull() {
        assertFalse(router.sendEmail("   ", "Subject", "<h1>Hello</h1>"));
        assertFalse(router.sendEmail(null, "Subject", "<h1>Hello</h1>"));
        verifyNoInteractions(mockResend);
        verifyNoInteractions(mockBrevo);
    }

    @Test
    void sendEmail_noFallbackToResend_whenBrevoFails() {
        when(mockBrevo.sendEmail(eq("other@example.com"), anyString(), anyString())).thenReturn(false);

        boolean result = router.sendEmail("other@example.com", "Digest", "<p>content</p>");

        assertFalse(result);
        verify(mockBrevo).sendEmail(eq("other@example.com"), anyString(), anyString());
        verifyNoInteractions(mockResend);
    }
}
