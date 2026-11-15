package com.growthprint.backend.ai.service;

import com.growthprint.backend.ai.provider.AiCompletion;
import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiException;
import com.growthprint.backend.ai.provider.AiProviderAdapters;
import com.growthprint.backend.ai.provider.AiRequest;
import com.growthprint.backend.ai.provider.ProviderConnection;
import com.growthprint.backend.ai.settings.AiSettingsService;
import com.growthprint.backend.ai.usage.AiUsageRecord;
import com.growthprint.backend.ai.usage.AiUsageRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single entry point business features use for AI. It never knows which vendor is behind it.
 * <ol>
 *     <li>Refuses when AI is off or incomplete (core workflows never depend on this).</li>
 *     <li>Enforces the per-user daily request limit before any provider call.</li>
 *     <li>Caps output tokens at the admin's setting.</li>
 *     <li>Records every call (success or failure) without prompt or response text. The limit check and a PENDING
 *     reservation happen together under a per-user lock, so concurrent requests cannot overshoot the limit
 *     (single application instance; with several instances the limit is approximate).</li>
 * </ol>
 * Deliberately not transactional: no database transaction is held open while waiting for a provider.
 */
@Service
public class AiService {

    /** Outcomes caused by the admin's configuration, not by the user's requests: they do not use up the user's quota. */
    private static final List<String> NOT_COUNTED = List.of("INVALID_CREDENTIALS", "UNSUPPORTED_MODEL", "ENDPOINT_NOT_ALLOWED");
    private static final Object NO_USER = new Object();

    private final ConcurrentHashMap<Object, Object> userLocks = new ConcurrentHashMap<>();
    private final AiSettingsService settings;
    private final AiProviderAdapters adapters;
    private final AiUsageRepository usageRepository;
    private final Clock clock;

    public AiService(AiSettingsService settings, AiProviderAdapters adapters, AiUsageRepository usageRepository, Clock clock) {
        this.settings = settings;
        this.adapters = adapters;
        this.usageRepository = usageRepository;
        this.clock = clock;
    }

    public AiCompletion complete(String feature, UUID userId, AiRequest request) {
        ProviderConnection connection = settings.activeConnection();

        Instant startOfDay = LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant started = clock.instant();
        AiUsageRecord reservation;
        synchronized (userLocks.computeIfAbsent(userId == null ? NO_USER : userId, k -> new Object())) {
            if (usageRepository.countTowardsLimit(userId, startOfDay, NOT_COUNTED) >= settings.dailyRequestLimitPerUser()) {
                throw new AiException(AiErrorCode.USAGE_LIMIT_REACHED);
            }
            reservation = usageRepository.save(new AiUsageRecord(userId, connection.type().name(), connection.model(),
                    feature, null, null, 0, "PENDING", started));
        }

        AiRequest capped = new AiRequest(request.systemPrompt(), request.userPrompt(), request.jsonOutput(),
                Math.min(request.maxOutputTokens(), settings.maxOutputTokens()));

        long startNanos = System.nanoTime();
        try {
            AiCompletion completion = adapters.complete(connection, capped);
            finish(reservation, completion.inputTokens(), completion.outputTokens(), startNanos, "SUCCESS");
            return completion;
        } catch (AiException e) {
            finish(reservation, null, null, startNanos, e.code().name());
            throw e;
        } catch (RuntimeException e) {
            // Anything unexpected is still recorded (and still counts), then surfaces as before.
            finish(reservation, null, null, startNanos, "INTERNAL_ERROR");
            throw e;
        }
    }

    private void finish(AiUsageRecord reservation, Integer inputTokens, Integer outputTokens, long startNanos, String outcome) {
        int latencyMs = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000);
        reservation.finish(inputTokens, outputTokens, latencyMs, outcome);
        usageRepository.save(reservation);
    }
}
