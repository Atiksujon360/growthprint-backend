package com.growthprint.backend.auth.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Fallback used while no email provider is configured. It sends nothing and logs a warning WITHOUT the link
 * or the address, so operators can see that a reset was requested but could not be delivered.
 * An admin can still issue a one-time reset link from the console (POST /api/v1/admin/users/{id}/password-reset-link).
 */
public class UnconfiguredAccountEmailSender implements AccountEmailSender {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredAccountEmailSender.class);

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void sendPasswordReset(String toEmail, String displayName, String resetLink, Duration validFor) {
        log.warn("Password reset requested for an existing account, but no email provider is configured: "
                + "no email was sent. Configure an AccountEmailSender bean (see SECURITY.md).");
    }
}
