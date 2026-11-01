package com.growthprint.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** Mutable clock so tests can move time forward deterministically. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksRequestsOverTheLimitWithinTheWindowEvenWhenSpreadOverTime() {
        // Regression: the old implementation reset the counter on (almost) every request
        // because a new bucket's window start was set to "now - window".
        TestClock clock = new TestClock();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("ip", 3, WINDOW)).isTrue();
            clock.advance(Duration.ofSeconds(5));
        }

        assertThat(limiter.tryAcquire("ip", 3, WINDOW)).isFalse();
    }

    @Test
    void allowsRequestsAgainOnceTheWindowHasElapsed() {
        TestClock clock = new TestClock();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        assertThat(limiter.tryAcquire("ip", 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("ip", 1, WINDOW)).isFalse();

        clock.advance(WINDOW);

        assertThat(limiter.tryAcquire("ip", 1, WINDOW)).isTrue();
    }

    @Test
    void keysAreLimitedIndependently() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new TestClock());

        assertThat(limiter.tryAcquire("a", 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, WINDOW)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, WINDOW)).isTrue();
    }

    @Test
    void expiredBucketsAreEvictedPeriodically() {
        TestClock clock = new TestClock();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        for (int i = 0; i < 500; i++) {
            limiter.tryAcquire("old-" + i, 10, WINDOW);
        }
        clock.advance(WINDOW.multipliedBy(3));
        for (int i = 0; i < 500; i++) {
            limiter.tryAcquire("new-" + i, 10, WINDOW);
        }

        assertThat(limiter.bucketCount()).isLessThanOrEqualTo(500);
    }
}
