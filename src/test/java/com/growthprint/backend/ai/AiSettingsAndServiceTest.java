package com.growthprint.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiException;
import com.growthprint.backend.ai.provider.AiProviderAdapters;
import com.growthprint.backend.ai.provider.AiProviderType;
import com.growthprint.backend.ai.provider.AiRequest;
import com.growthprint.backend.ai.service.AiService;
import com.growthprint.backend.ai.settings.AiConnectionStatus;
import com.growthprint.backend.ai.settings.AiCredentialCipher;
import com.growthprint.backend.ai.settings.AiSettings;
import com.growthprint.backend.ai.settings.AiSettingsRepository;
import com.growthprint.backend.ai.settings.AiSettingsService;
import com.growthprint.backend.ai.settings.AiSettingsView;
import com.growthprint.backend.ai.settings.UpdateAiSettingsCommand;
import com.growthprint.backend.ai.usage.AiUsageRecord;
import com.growthprint.backend.ai.usage.AiUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSettingsAndServiceTest {

    private static final String KEY = "sk-test-not-a-real-key-123456";
    private static final UUID ADMIN = UUID.randomUUID();

    private final AiSettingsRepository settingsRepository = mock(AiSettingsRepository.class);
    private final AiUsageRepository usageRepository = mock(AiUsageRepository.class);
    private final AiCredentialCipher cipher = new AiCredentialCipher(AiCredentialCipherTest.randomKey());
    private final AiProviderAdapters adapters = new AiProviderAdapters(new ObjectMapper());
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private AiSettings stored;
    private AiSettingsService settingsService;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        stored = AiSettings.defaults();
        when(settingsRepository.findById(AiSettings.SINGLETON_ID)).thenAnswer(i -> Optional.of(stored));
        when(settingsRepository.save(any(AiSettings.class))).thenAnswer(i -> stored = i.getArgument(0));
        settingsService = new AiSettingsService(settingsRepository, cipher, adapters, clock);
        aiService = new AiService(settingsService, adapters, usageRepository, clock);
    }

    // ---- settings ----------------------------------------------------------------------------

    @Test
    void aiIsOffAndUnconfiguredByDefault() {
        AiSettingsView view = settingsService.get();
        assertThat(view.enabled()).isFalse();
        assertThat(view.status()).isEqualTo(AiConnectionStatus.AI_DISABLED);
        assertThat(view.apiKeyConfigured()).isFalse();
    }

    @Test
    void savedKeyIsEncryptedAtRestAndOnlyEverReturnedMasked() {
        settingsService.update(new UpdateAiSettingsCommand(false, AiProviderType.OPENAI, "gpt-test", null, 30, 800, 50), ADMIN);
        AiSettingsView view = settingsService.setApiKey(KEY, ADMIN);

        assertThat(stored.getApiKeyCiphertext()).isNotBlank().doesNotContain(KEY);
        assertThat(view.apiKeyConfigured()).isTrue();
        assertThat(view.apiKeyMasked()).isEqualTo("••••3456");
        assertThat(view.toString()).doesNotContain(KEY);
        assertThat(view.status()).isEqualTo(AiConnectionStatus.AI_DISABLED);
    }

    @Test
    void switchingProviderRemovesTheOldProvidersKey() {
        settingsService.update(new UpdateAiSettingsCommand(false, AiProviderType.OPENAI, "gpt-test", null, 30, 800, 50), ADMIN);
        settingsService.setApiKey(KEY, ADMIN);

        AiSettingsView view = settingsService.update(
                new UpdateAiSettingsCommand(false, AiProviderType.GEMINI, "gemini-test", null, 30, 800, 50), ADMIN);

        assertThat(view.apiKeyConfigured()).isFalse();
        assertThat(stored.getApiKeyCiphertext()).isNull();
    }

    @Test
    void aiCannotBeSwitchedOnWithoutProviderModelAndKey() {
        assertThatThrownBy(() -> settingsService.update(
                new UpdateAiSettingsCommand(true, AiProviderType.OPENAI, "gpt-test", null, 30, 800, 50), ADMIN))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.KEY_MISSING));
    }

    @Test
    void customEndpointRequiresHttpsExceptForLocalhost() {
        assertThatThrownBy(() -> settingsService.update(new UpdateAiSettingsCommand(
                false, AiProviderType.OPENAI_COMPATIBLE, "m", "http://models.example.com/v1", 30, 800, 50), ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settingsService.update(new UpdateAiSettingsCommand(
                false, AiProviderType.OPENAI_COMPATIBLE, "m", null, 30, 800, 50), ADMIN))
                .isInstanceOf(IllegalArgumentException.class);

        AiSettingsView view = settingsService.update(new UpdateAiSettingsCommand(
                false, AiProviderType.OPENAI_COMPATIBLE, "m", "https://models.example.com/v1", 30, 800, 50), ADMIN);
        assertThat(view.baseUrl()).isEqualTo("https://models.example.com/v1");
    }

    @Test
    void builtInProvidersIgnoreACustomBaseUrl() {
        AiSettingsView view = settingsService.update(new UpdateAiSettingsCommand(
                false, AiProviderType.OPENAI, "m", "https://attacker.example.com", 30, 800, 50), ADMIN);
        assertThat(view.baseUrl()).isNull();
    }

    @Test
    void connectionTestRecordsASafeResultWithoutTheKey() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(
                FakeProviderServer.Reply.status(401, "{\"error\":{\"message\":\"bad key " + KEY + "\"}}"))) {
            settingsService.update(new UpdateAiSettingsCommand(
                    false, AiProviderType.OPENAI_COMPATIBLE, "m", server.baseUrl(), 5, 800, 50), ADMIN);
            settingsService.setApiKey(KEY, ADMIN);

            AiSettingsView view = settingsService.testConnection(ADMIN);

            assertThat(view.status()).isEqualTo(AiConnectionStatus.INVALID_CREDENTIALS);
            assertThat(view.lastTestAt()).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
            assertThat(view.lastTestMessage()).doesNotContain(KEY);
        }
    }

    // ---- AI service --------------------------------------------------------------------------

    @Test
    void disabledAiIsRefusedWithoutCallingAnyProvider() {
        assertThatThrownBy(() -> aiService.complete("support_triage", ADMIN, new AiRequest("s", "u", true, 100)))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.AI_DISABLED));
        verify(usageRepository, never()).save(any());
    }

    @Test
    void successfulCallIsRecordedWithTokensButNeverThePrompt() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.ok(
                "{\"choices\":[{\"message\":{\"content\":\"{}\"}}],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":2}}"))) {
            enableWith(server);

            aiService.complete("support_triage", ADMIN, new AiRequest("system", "SECRET CUSTOMER TEXT", true, 100));

            ArgumentCaptor<AiUsageRecord> usage = ArgumentCaptor.forClass(AiUsageRecord.class);
            verify(usageRepository).save(usage.capture());
            AiUsageRecord record = usage.getValue();
            assertThat(record.getOutcome()).isEqualTo("SUCCESS");
            assertThat(record.getInputTokens()).isEqualTo(11);
            assertThat(record.getOutputTokens()).isEqualTo(2);
            assertThat(record.getFeature()).isEqualTo("support_triage");
            assertThat(record.toString()).doesNotContain("SECRET CUSTOMER TEXT");
        }
    }

    @Test
    void dailyLimitPerUserIsEnforcedBeforeCallingTheProvider() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.ok("{}"))) {
            enableWith(server);
            when(usageRepository.countByUserIdAndCreatedAtGreaterThanEqual(eq(ADMIN), any())).thenReturn(50L);

            assertThatThrownBy(() -> aiService.complete("support_triage", ADMIN, new AiRequest("s", "u", true, 100)))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.USAGE_LIMIT_REACHED));
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void failedCallIsRecordedWithItsErrorCode() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.status(401, "{}"))) {
            enableWith(server);
            assertThatThrownBy(() -> aiService.complete("support_triage", ADMIN, new AiRequest("s", "u", true, 100)))
                    .isInstanceOf(AiException.class);

            ArgumentCaptor<AiUsageRecord> usage = ArgumentCaptor.forClass(AiUsageRecord.class);
            verify(usageRepository).save(usage.capture());
            assertThat(usage.getValue().getOutcome()).isEqualTo("INVALID_CREDENTIALS");
        }
    }

    private void enableWith(FakeProviderServer server) {
        settingsService.update(new UpdateAiSettingsCommand(
                false, AiProviderType.OPENAI_COMPATIBLE, "m", server.baseUrl(), 5, 800, 50), ADMIN);
        settingsService.setApiKey(KEY, ADMIN);
        settingsService.update(new UpdateAiSettingsCommand(
                true, AiProviderType.OPENAI_COMPATIBLE, "m", server.baseUrl(), 5, 800, 50), ADMIN);
    }
}
