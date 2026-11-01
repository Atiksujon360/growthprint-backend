package com.growthprint.backend.ai.settings;

import com.growthprint.backend.ai.provider.AiProviderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * AI settings as returned to the admin. Never contains the API key: only whether one is saved and a masked
 * form showing its last four characters.
 */
@Schema(description = "AI provider settings (the API key itself is never returned)")
public record AiSettingsView(
        boolean enabled,
        AiProviderType provider,
        String providerDisplayName,
        String model,
        @Schema(description = "Only set for the custom OpenAI-compatible provider") String baseUrl,
        int timeoutSeconds,
        int maxOutputTokens,
        int dailyRequestLimitPerUser,
        boolean apiKeyConfigured,
        @Schema(example = "••••3456") String apiKeyMasked,
        @Schema(description = "False when the server has no AI_CREDENTIALS_ENCRYPTION_KEY, so keys can't be saved")
        boolean encryptionConfigured,
        AiConnectionStatus status,
        Instant lastTestAt,
        String lastTestMessage,
        Instant updatedAt
) {
}
