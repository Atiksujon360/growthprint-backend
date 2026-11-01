package com.growthprint.backend.ai.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One AI call, for limits and reporting. Deliberately has no prompt or response text: only who, which
 * provider/model/feature, provider-reported token counts (null when not reported, never estimated),
 * latency and outcome.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsageRecord {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(nullable = false, length = 50)
    private String feature;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(nullable = false, length = 40)
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiUsageRecord() {
    }

    public AiUsageRecord(UUID userId, String provider, String model, String feature, Integer inputTokens,
                         Integer outputTokens, int latencyMs, String outcome, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.provider = provider;
        this.model = model;
        this.feature = feature;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.outcome = outcome;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getFeature() { return feature; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public int getLatencyMs() { return latencyMs; }
    public String getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "AiUsageRecord[feature=" + feature + ", provider=" + provider + ", model=" + model
                + ", outcome=" + outcome + ", inputTokens=" + inputTokens + ", outputTokens=" + outputTokens + "]";
    }
}
