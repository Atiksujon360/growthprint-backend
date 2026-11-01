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
import java.util.UUID;

/**
 * The single entry point business features use for AI. It never knows which vendor is behind it.
 * <ol>
 *     <li>Refuses when AI is off or incomplete (core workflows never depend on this).</li>
 *     <li>Enforces the per-user daily request limit before any provider call.</li>
 *     <li>Caps output tokens at the admin's setting.</li>
 *     <li>Records every call (success or failure) without prompt or response text.</li>
 * </ol>
 * Deliberately not transactional: no database transaction is held open while waiting for a provider.
 */
@Service
public class AiService {

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
        if (usageRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, startOfDay) >= settings.dailyRequestLimitPerUser()) {
            throw new AiException(AiErrorCode.USAGE_LIMIT_REACHED);
        }

        AiRequest capped = new AiRequest(request.systemPrompt(), request.userPrompt(), request.jsonOutput(),
                Math.min(request.maxOutputTokens(), settings.maxOutputTokens()));

        Instant started = clock.instant();
        long startNanos = System.nanoTime();
        try {
            AiCompletion completion = adapters.complete(connection, capped);
            record(userId, connection, feature, completion.inputTokens(), completion.outputTokens(), startNanos, "SUCCESS", started);
            return completion;
        } catch (AiException e) {
            record(userId, connection, feature, null, null, startNanos, e.code().name(), started);
            throw e;
        }
    }

    private void record(UUID userId, ProviderConnection connection, String feature, Integer inputTokens,
                        Integer outputTokens, long startNanos, String outcome, Instant at) {
        int latencyMs = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000);
        usageRepository.save(new AiUsageRecord(userId, connection.type().name(), connection.model(), feature,
                inputTokens, outputTokens, latencyMs, outcome, at));
    }
}
