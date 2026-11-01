package com.growthprint.backend.ai.settings;

import com.growthprint.backend.ai.provider.AiProviderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin update of the AI configuration (the API key is set separately and is write-only).
 * Changing the provider removes the saved key, because a key belongs to one provider.
 */
public record UpdateAiSettingsCommand(
        boolean enabled,
        AiProviderType provider,
        @Size(max = 120, message = "Model ID must not exceed 120 characters")
        @Pattern(regexp = "^[A-Za-z0-9._:/@-]*$", message = "Model ID may only contain letters, numbers and . _ : / @ -")
        String model,
        @Size(max = 500, message = "Base URL must not exceed 500 characters")
        String baseUrl,
        @Min(value = 5, message = "Timeout must be at least 5 seconds")
        @Max(value = 120, message = "Timeout must be at most 120 seconds")
        int timeoutSeconds,
        @Min(value = 64, message = "Output limit must be at least 64 tokens")
        @Max(value = 8000, message = "Output limit must be at most 8000 tokens")
        int maxOutputTokens,
        @Min(value = 1, message = "Daily limit must be at least 1")
        @Max(value = 10000, message = "Daily limit must be at most 10000")
        int dailyRequestLimitPerUser
) {
}
