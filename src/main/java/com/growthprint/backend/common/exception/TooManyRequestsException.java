package com.growthprint.backend.common.exception;

import java.time.Duration;

/** Rate limit hit inside a service (per-account throttles). Mapped to 429 with Retry-After. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super("Too many attempts. Please wait and try again.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
