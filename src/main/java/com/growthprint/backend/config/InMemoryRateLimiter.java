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

        // Clean up old entries periodically
        if (now.getEpochSecond() % 60 == 0) {
            buckets.entrySet().removeIf(entry -> entry.getValue().windowStart.isBefore(windowStart));
        }

        return bucket.count.get() <= maxRequests;
    }

    public String getClientIdentifier(HttpServletRequest request) {
        // In production, use a trusted proxy configuration to get real client IP
        // For now, use remote address as a simple identifier
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
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
