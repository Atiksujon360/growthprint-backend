package com.growthprint.backend.auth.notification;

import java.time.Duration;

/**
 * Delivers account emails (password reset). Deliberately an interface: no email provider is configured for
 * this project yet (see SECURITY.md, "Password recovery"). Until a real implementation is registered as a
 * Spring bean, {@link UnconfiguredAccountEmailSender} is used and NO email is sent.
 * <p>
 * Implementations must never log the link: it is a bearer credential until used or expired.
 */
public interface AccountEmailSender {

    /** True when emails are actually delivered. Shown to admins, never to anonymous callers. */
    boolean isConfigured();

    void sendPasswordReset(String toEmail, String displayName, String resetLink, Duration validFor);
}
