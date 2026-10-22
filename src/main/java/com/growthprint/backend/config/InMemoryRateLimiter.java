package com.growthprint.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryRateLimiter {

    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        RateLimitBucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart.isBefore(windowStart)) {
                return new RateLimitBucket(windowStart, 1);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        // Clean up old entries more aggressively to prevent memory leaks
        // Every 60 seconds, remove entries older than 2x the configured window
        if (now.getEpochSecond() % 60 == 0) {
            Instant cleanupThreshold = now.minus(window.multipliedBy(2));
            buckets.entrySet().removeIf(entry -> entry.getValue().windowStart.isBefore(cleanupThreshold));
        }

        return bucket.count.get() <= maxRequests;
    }

    public String getClientIdentifier(HttpServletRequest request) {
        // WARNING: Do not blindly trust X-Forwarded-For headers from untrusted clients.
        // This header can be spoofed. Only use it when behind a trusted reverse proxy
        // that validates and overwrites the header. For now, use remote address only.
        // Future enhancement: Add app.trusted-proxies configuration and implement
        // trusted proxy validation before using X-Forwarded-For.
        return request.getRemoteAddr();
    }

    private static class RateLimitBucket {
        final Instant windowStart;
        final AtomicInteger count;

        RateLimitBucket(Instant windowStart, int initialCount) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(initialCount);
        }
    }
}
