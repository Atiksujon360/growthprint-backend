package com.growthprint.backend.ai.provider;

import org.springframework.http.HttpStatus;

/** Provider-independent AI errors, each with a safe, human message and an HTTP status for our API. */
public enum AiErrorCode {
    AI_DISABLED(HttpStatus.CONFLICT, "AI features are turned off. An admin can turn them on under Settings → AI providers."),
    NOT_CONFIGURED(HttpStatus.CONFLICT, "No AI provider and model have been set up yet."),
    KEY_MISSING(HttpStatus.CONFLICT, "Add an API key for the selected AI provider first."),
    ENCRYPTION_KEY_MISSING(HttpStatus.CONFLICT,
            "API keys can't be saved until the server's AI_CREDENTIALS_ENCRYPTION_KEY is configured."),
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
