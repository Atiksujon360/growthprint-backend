package com.growthprint.backend.ai.provider;

/**
 * Normalised provider answer. Token counts are what the provider reported, or null when it reported none
 * (they are never estimated).
 */
public record AiCompletion(String text, Integer inputTokens, Integer outputTokens, String provider, String model) {

    public AiCompletion withSource(String provider, String model) {
        return new AiCompletion(text, inputTokens, outputTokens, provider, model);
    }
}
