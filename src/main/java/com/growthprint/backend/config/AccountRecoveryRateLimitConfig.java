package com.growthprint.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Per-IP limit shared by the public account-recovery and invitation endpoints. */
@Component
@ConfigurationProperties(prefix = "app.rate-limit.account-recovery")
public class AccountRecoveryRateLimitConfig {

    private int maxRequests = 10;
    private Duration window = Duration.ofMinutes(15);

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
