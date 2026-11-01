package com.growthprint.backend.ai.provider;

import java.time.Duration;

/** Everything needed to call one provider. Holds the decrypted key only in memory, never printed. */
public record ProviderConnection(AiProviderType type, String baseUrl, String apiKey, String model, Duration timeout) {

    @Override
    public String toString() {
        return "ProviderConnection[type=" + type + ", baseUrl=" + baseUrl + ", model=" + model
                + ", timeout=" + timeout + ", apiKey=****]";
    }
}
