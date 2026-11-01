package com.growthprint.backend.ai.settings;

import com.growthprint.backend.ai.provider.AiErrorCode;

/** What the admin sees about the AI configuration (shown by the settings screen). */
public enum AiConnectionStatus {
    AI_DISABLED,
    NOT_CONFIGURED,
    KEY_MISSING,
    NOT_TESTED,
    CONNECTED,
    INVALID_CREDENTIALS,
    UNSUPPORTED_MODEL,
    RATE_LIMITED,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    PROVIDER_ERROR;

    static AiConnectionStatus fromError(AiErrorCode code) {
        return switch (code) {
            case INVALID_CREDENTIALS -> INVALID_CREDENTIALS;
            case UNSUPPORTED_MODEL -> UNSUPPORTED_MODEL;
            case RATE_LIMITED -> RATE_LIMITED;
            case TIMEOUT -> TIMEOUT;
            case PROVIDER_UNAVAILABLE -> PROVIDER_UNAVAILABLE;
            case KEY_MISSING, ENCRYPTION_KEY_MISSING -> KEY_MISSING;
            case NOT_CONFIGURED -> NOT_CONFIGURED;
            default -> PROVIDER_ERROR;
        };
    }
}
