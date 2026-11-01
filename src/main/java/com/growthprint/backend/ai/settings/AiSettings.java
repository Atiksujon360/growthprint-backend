package com.growthprint.backend.ai.settings;

import com.growthprint.backend.ai.provider.AiProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The single, company-wide AI configuration row (id = 1). Only an admin changes it.
 * The API key is stored encrypted (see {@link AiCredentialCipher}); only its last four characters are kept
 * in clear, for the masked display.
 */
@Entity
@Table(name = "ai_settings")
public class AiSettings {

    public static final Short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AiProviderType provider;

    @Column(length = 120)
    private String model;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens;

    @Column(name = "daily_request_limit_per_user", nullable = false)
    private int dailyRequestLimitPerUser;

    @Column(name = "api_key_ciphertext", columnDefinition = "TEXT")
    private String apiKeyCiphertext;

    @Column(name = "api_key_iv", length = 32)
    private String apiKeyIv;

    @Column(name = "api_key_last4", length = 4)
    private String apiKeyLast4;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_status", length = 30)
    private AiConnectionStatus lastTestStatus;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_message", length = 300)
    private String lastTestMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    protected AiSettings() {
    }

    /** Matches the row inserted by migration V19: AI off, nothing configured. */
    public static AiSettings defaults() {
        AiSettings settings = new AiSettings();
        settings.id = SINGLETON_ID;
        settings.enabled = false;
        settings.timeoutSeconds = 60;
        settings.maxOutputTokens = 800;
        settings.dailyRequestLimitPerUser = 50;
        settings.updatedAt = Instant.now();
        return settings;
    }

    void applyConfiguration(boolean enabled, AiProviderType provider, String model, String baseUrl,
                            int timeoutSeconds, int maxOutputTokens, int dailyLimit, UUID adminId, Instant now) {
        this.enabled = enabled;
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.dailyRequestLimitPerUser = dailyLimit;
        touch(adminId, now);
    }

    void storeKey(AiCredentialCipher.EncryptedSecret secret, String last4, UUID adminId, Instant now) {
        this.apiKeyCiphertext = secret.ciphertext();
        this.apiKeyIv = secret.iv();
        this.apiKeyLast4 = last4;
        clearTestResult();
        touch(adminId, now);
    }

    void removeKey(UUID adminId, Instant now) {
        this.apiKeyCiphertext = null;
        this.apiKeyIv = null;
        this.apiKeyLast4 = null;
        this.enabled = false;
        clearTestResult();
        touch(adminId, now);
    }

    void recordTest(AiConnectionStatus status, String message, Instant now) {
        this.lastTestStatus = status;
        this.lastTestMessage = message;
        this.lastTestAt = now;
    }

    void clearTestResult() {
        this.lastTestStatus = null;
        this.lastTestMessage = null;
        this.lastTestAt = null;
    }

    private void touch(UUID adminId, Instant now) {
        this.updatedByUserId = adminId;
        this.updatedAt = now;
    }

    boolean hasKey() {
        return apiKeyCiphertext != null && apiKeyIv != null;
    }

    AiCredentialCipher.EncryptedSecret encryptedKey() {
        return new AiCredentialCipher.EncryptedSecret(apiKeyCiphertext, apiKeyIv);
    }

    public Short getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public AiProviderType getProvider() { return provider; }
    public String getModel() { return model; }
    public String getBaseUrl() { return baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public int getDailyRequestLimitPerUser() { return dailyRequestLimitPerUser; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public String getApiKeyLast4() { return apiKeyLast4; }
    public AiConnectionStatus getLastTestStatus() { return lastTestStatus; }
    public Instant getLastTestAt() { return lastTestAt; }
    public String getLastTestMessage() { return lastTestMessage; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "AiSettings[enabled=" + enabled + ", provider=" + provider + ", model=" + model + ", apiKey=****]";
    }
}
