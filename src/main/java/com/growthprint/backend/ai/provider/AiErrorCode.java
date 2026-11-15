package com.growthprint.backend.ai.provider;

import org.springframework.http.HttpStatus;

/** Provider-independent AI errors, each with a safe, human message and an HTTP status for our API. */
public enum AiErrorCode {
    AI_DISABLED(HttpStatus.CONFLICT, "AI features are turned off. An admin can turn them on under Settings → AI providers."),
    NOT_CONFIGURED(HttpStatus.CONFLICT, "No AI provider and model have been set up yet."),
    KEY_MISSING(HttpStatus.CONFLICT, "Add an API key for the selected AI provider first."),
    ENCRYPTION_KEY_MISSING(HttpStatus.CONFLICT,
            "API keys can't be saved until the server's AI_CREDENTIALS_ENCRYPTION_KEY is configured."),
    KEY_UNREADABLE(HttpStatus.CONFLICT,
            "The saved API key can't be read with this server's encryption key (it may have changed). Enter the API key again."),
    ENDPOINT_NOT_ALLOWED(HttpStatus.BAD_REQUEST,
            "That endpoint address isn't allowed. Use a public https:// address, not a private or internal one."),
    KEY_RESET_NOT_CONFIRMED(HttpStatus.CONFLICT,
            "Changing the provider or endpoint removes the saved API key. Confirm to continue, then enter the key again."),
    CONNECTION_NOT_TESTED(HttpStatus.CONFLICT,
            "Test the connection successfully before turning AI on."),
    USAGE_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "You've reached today's AI request limit. Please try again tomorrow."),
    INVALID_CREDENTIALS(HttpStatus.BAD_GATEWAY, "The AI provider rejected the API key."),
    UNSUPPORTED_MODEL(HttpStatus.BAD_GATEWAY, "The AI provider doesn't recognise that model."),
    RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is rate limiting requests right now. Please try again shortly."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "The AI provider took too long to respond."),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is unavailable right now."),
    PROVIDER_REJECTED(HttpStatus.BAD_GATEWAY, "The AI provider rejected the request."),
    MALFORMED_RESPONSE(HttpStatus.BAD_GATEWAY, "The AI provider returned a response we couldn't use.");

    private final HttpStatus httpStatus;
    private final String message;

    AiErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
