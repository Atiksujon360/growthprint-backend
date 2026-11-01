package com.growthprint.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Brute-force protection settings for POST /api/v1/auth/login (per client IP).
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit.login")
public class LoginRateLimitConfig {

    private int maxRequests = 10;
    private Duration window = Duration.ofMinutes(1);

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }
}
