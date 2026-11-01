package com.growthprint.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple fixed-window, in-memory rate limiter (single application instance only).
 * Each key gets a window that starts at its first request and lasts {@code window};
 * at most {@code maxRequests} requests are allowed inside that window.
 */
@Component
public class InMemoryRateLimiter {

    private static final int CLEANUP_EVERY_N_CALLS = 1_000;

    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong();
    private final Clock clock;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        Instant now = clock.instant();

        RateLimitBucket bucket = buckets.compute(key, (k, existing) -> {
            // Start a new window when there is none yet or the previous one has fully elapsed.
            if (existing == null || !now.isBefore(existing.windowStart.plus(window))) {
                return new RateLimitBucket(now, 1);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        // Periodically evict expired buckets so the map cannot grow without bound.
        if (callCounter.incrementAndGet() % CLEANUP_EVERY_N_CALLS == 0) {
            Instant cleanupThreshold = now.minus(window.multipliedBy(2));
            buckets.entrySet().removeIf(entry -> entry.getValue().windowStart.isBefore(cleanupThreshold));
        }

        return bucket.count.get() <= maxRequests;
    }

    public String getClientIdentifier(HttpServletRequest request) {
        // Do not read X-Forwarded-For here: clients can spoof it.
        // server.forward-headers-strategy=native makes Tomcat's RemoteIpValve rewrite
        // getRemoteAddr() from X-Forwarded-For only when the connection comes from a trusted
        // internal proxy (e.g. Render's load balancer), so remoteAddr is the real client IP.
        return request.getRemoteAddr();
    }

    int bucketCount() {
        return buckets.size();
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
