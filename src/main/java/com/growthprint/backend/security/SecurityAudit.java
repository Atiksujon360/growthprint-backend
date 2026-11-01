package com.growthprint.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Structured audit trail for security-sensitive operations, written to the dedicated {@code SECURITY_AUDIT}
 * logger (route it to its own appender/retention in production).
 * <p>
 * Never pass passwords, tokens, reset links or email addresses: events identify accounts by user id only.
 */
public final class SecurityAudit {

    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    public enum Event {
        LOGIN_SUCCEEDED,
        LOGIN_FAILED,
        LOGIN_THROTTLED,
        PASSWORD_CHANGED,
        PASSWORD_CHANGE_REJECTED,
        PASSWORD_RESET_REQUESTED,
        PASSWORD_RESET_COMPLETED,
        PASSWORD_RESET_REJECTED,
        SESSIONS_REVOKED,
        PROFILE_UPDATED,
        INVITATION_CREATED,
        INVITATION_ACCEPTED,
        INVITATION_REJECTED,
        ACCOUNT_STATUS_CHANGED,
        ROLE_CHANGED,
        PERMISSIONS_CHANGED
    }

    private SecurityAudit() {
    }

    /**
     * @param actorId  who performed the action (null for anonymous)
     * @param targetId whose account was affected (null if unknown, e.g. a reset for an unknown address)
     * @param detail   short, non-sensitive detail (status names, module names), may be null
     */
    public static void record(Event event, UUID actorId, UUID targetId, String detail) {
        AUDIT.info("event={} actor={} target={} detail={}", event, idOrDash(actorId), idOrDash(targetId),
                detail == null ? "-" : detail.replaceAll("[\\r\\n]", " "));
    }

    private static String idOrDash(UUID id) {
        return id == null ? "-" : id.toString();
    }
}
