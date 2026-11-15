package com.growthprint.backend.ai;

import com.growthprint.backend.ai.provider.AiCompletion;
import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiException;
import com.growthprint.backend.ai.provider.AiProviderAdapters;
import com.growthprint.backend.ai.provider.AiProviderType;
import com.growthprint.backend.ai.provider.AiRequest;
import com.growthprint.backend.ai.provider.ProviderConnection;
import com.growthprint.backend.ai.service.AiService;
import com.growthprint.backend.ai.settings.AiSettingsService;
import com.growthprint.backend.ai.usage.AiUsageRecord;
import com.growthprint.backend.ai.usage.AiUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Daily limit behaviour of {@link AiService}: atomic under concurrency, honest about what counts. */
class AiUsageLimitTest {

    private static final UUID USER = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private AiSettingsService settings;
    private AiProviderAdapters adapters;
    private AiUsageRepository usage;
    private final List<AiUsageRecord> rows = new CopyOnWriteArrayList<>();
    private AiService service;

    @BeforeEach
    void setUp() {
        settings = mock(AiSettingsService.class);
        adapters = mock(AiProviderAdapters.class);
        usage = mock(AiUsageRepository.class);
        when(settings.activeConnection()).thenReturn(new ProviderConnection(
                AiProviderType.OPENAI, "https://api.openai.com/v1", "k", "m", Duration.ofSeconds(5)));
        when(settings.dailyRequestLimitPerUser()).thenReturn(3);
        when(settings.maxOutputTokens()).thenReturn(800);
        when(usage.save(any(AiUsageRecord.class))).thenAnswer(i -> {
            AiUsageRecord r = i.getArgument(0);
            if (!rows.contains(r)) {
                rows.add(r);
            }
            return r;
        });
        when(usage.countTowardsLimit(eq(USER), any(), anyCollection())).thenAnswer(i -> {
            Collection<String> notCounted = i.getArgument(2);
            return rows.stream().filter(r -> !notCounted.contains(r.getOutcome())).count();
        });
        service = new AiService(settings, adapters, usage, clock);
    }

    @Test
    void concurrentRequestsCannotOvershootTheDailyLimit() throws Exception {
        when(adapters.complete(any(), any())).thenAnswer(i -> {
            Thread.sleep(40); // a slow provider widens the race window
            return new AiCompletion("{}", 1, 1, "OPENAI", "m");
        });

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    service.complete("test", USER, new AiRequest("s", "u", false, 100));
                    succeeded.incrementAndGet();
                } catch (AiException e) {
                    if (e.code() == AiErrorCode.USAGE_LIMIT_REACHED) {
                        refused.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).as("calls allowed by a limit of 3").isEqualTo(3);
        assertThat(refused.get()).isEqualTo(threads - 3);
        assertThat(rows).hasSize(3);
    }

    @Test
    void anUnexpectedErrorIsStillRecordedAndRethrown() {
        when(adapters.complete(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.complete("test", USER, new AiRequest("s", "u", false, 100)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOutcome()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void failuresCausedByTheAdminsConfigurationDoNotUseUpTheUsersQuota() {
        when(adapters.complete(any(), any())).thenThrow(new AiException(AiErrorCode.INVALID_CREDENTIALS));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.complete("test", USER, new AiRequest("s", "u", false, 100)))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.INVALID_CREDENTIALS));
        }
        // Five bad-key failures with a limit of 3: the user was never refused for quota.
        assertThat(rows).hasSize(5);
    }

    @Test
    void aSuccessfulCallReplacesItsPendingReservation() {
        when(adapters.complete(any(), any())).thenReturn(new AiCompletion("{}", 7, 3, "OPENAI", "m"));

        service.complete("test", USER, new AiRequest("s", "u", false, 100));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOutcome()).isEqualTo("SUCCESS");
        assertThat(rows.get(0).getInputTokens()).isEqualTo(7);
    }
}
