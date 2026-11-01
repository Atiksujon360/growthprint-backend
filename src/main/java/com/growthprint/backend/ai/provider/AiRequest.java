package com.growthprint.backend.ai.provider;

/**
 * One generation request.
 *
 * @param systemPrompt    instructions from GrowthPrint (trusted)
 * @param userPrompt      the task, which may embed untrusted business text
 * @param jsonOutput      ask the provider for a JSON object (JSON mode / response MIME type)
 * @param maxOutputTokens upper bound for generated tokens (further capped by the admin setting)
 */
public record AiRequest(String systemPrompt, String userPrompt, boolean jsonOutput, int maxOutputTokens) {

    @Override
    public String toString() {
        return "AiRequest[jsonOutput=" + jsonOutput + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
