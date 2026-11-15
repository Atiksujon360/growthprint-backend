package com.growthprint.backend.ai.settings;

import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiEndpointGuard;
import com.growthprint.backend.ai.provider.AiException;
import com.growthprint.backend.ai.provider.AiProviderAdapters;
import com.growthprint.backend.ai.provider.AiProviderType;
import com.growthprint.backend.ai.provider.AiRequest;
import com.growthprint.backend.ai.provider.ProviderConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Admin-managed AI configuration: one active provider, model, limits and an encrypted API key.
 * <ul>
 *     <li>AI is off by default and can only be turned on once provider, model and key are set.</li>
 *     <li>The key is write-only: it is encrypted before saving and only ever returned masked.</li>
 *     <li>Built-in providers always use their official endpoint; only the custom OpenAI-compatible provider
 *     takes an admin-supplied URL, which must be HTTPS and resolve to public addresses ({@link AiEndpointGuard}).</li>
 *     <li>AI can only be turned on after a successful connection test; changing the provider, model or endpoint
 *     clears that test result.</li>
 *     <li>A key belongs to one provider and host: changing either discards it, and only with explicit confirmation.</li>
 *     <li>A connection test runs only when the admin asks for it, and no database transaction is held open while
 *     waiting for the provider.</li>
 * </ul>
 */
@Service
public class AiSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AiSettingsService.class);

    private final AiSettingsRepository repository;
    private final AiCredentialCipher cipher;
    private final AiProviderAdapters adapters;
    private final Clock clock;
    private final AiEndpointGuard endpointGuard;

    public AiSettingsService(AiSettingsRepository repository, AiCredentialCipher cipher,
                             AiProviderAdapters adapters, Clock clock, AiEndpointGuard endpointGuard) {
        this.endpointGuard = endpointGuard;
        this.repository = repository;
        this.cipher = cipher;
        this.adapters = adapters;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiSettingsView get() {
        return toView(load());
    }

    @Transactional
    public AiSettingsView update(UpdateAiSettingsCommand command, UUID adminId) {
        AiSettings settings = load();
        AiProviderType provider = command.provider();
        String model = blankToNull(command.model());
        String baseUrl = resolveBaseUrl(provider, command.baseUrl());

        boolean providerChanged = settings.getProvider() != provider;
        boolean endpointChanged = !java.util.Objects.equals(settings.getBaseUrl(), baseUrl);
        boolean connectionChanged = providerChanged
                || endpointChanged
                || !java.util.Objects.equals(settings.getModel(), model);

        if ((providerChanged || endpointChanged) && settings.hasKey()) {
            // A key belongs to one provider and one host; never send it somewhere else. The admin must agree to losing it.
            if (!command.keyResetConfirmed()) {
                throw new AiException(AiErrorCode.KEY_RESET_NOT_CONFIRMED);
            }
            settings.removeKey(adminId, clock.instant());
        }

        if (command.enabled()) {
            if (provider == null || model == null) {
                throw new AiException(AiErrorCode.NOT_CONFIGURED);
            }
            if (!settings.hasKey()) {
                throw new AiException(AiErrorCode.KEY_MISSING);
            }
            if (!cipher.canDecrypt(settings.encryptedKey())) {
                throw new AiException(cipher.isConfigured() ? AiErrorCode.KEY_UNREADABLE : AiErrorCode.ENCRYPTION_KEY_MISSING);
            }
            // Only the unchanged, already-tested connection may be switched on (a change below clears the test).
            if (connectionChanged || settings.getLastTestStatus() != AiConnectionStatus.CONNECTED) {
                throw new AiException(AiErrorCode.CONNECTION_NOT_TESTED);
            }
        }

        settings.applyConfiguration(command.enabled(), provider, model, baseUrl, command.timeoutSeconds(),
                command.maxOutputTokens(), command.dailyRequestLimitPerUser(), adminId, clock.instant());
        if (connectionChanged) {
            settings.clearTestResult();
        }
        log.info("AI settings updated by {}: enabled={}, provider={}, model={}", adminId, command.enabled(), provider, model);
        return toView(repository.save(settings));
    }

    @Transactional
    public AiSettingsView setApiKey(String apiKey, UUID adminId) {
        AiSettings settings = load();
        if (settings.getProvider() == null) {
            throw new AiException(AiErrorCode.NOT_CONFIGURED);
        }
        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.length() < 8 || trimmed.length() > 500 || trimmed.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("That doesn't look like a valid API key.");
        }
        settings.storeKey(cipher.encrypt(trimmed), trimmed.substring(trimmed.length() - 4), adminId, clock.instant());
        log.info("AI provider key replaced by {} for provider {}", adminId, settings.getProvider());
        return toView(repository.save(settings));
    }

    @Transactional
    public AiSettingsView removeApiKey(UUID adminId) {
        AiSettings settings = load();
        settings.removeKey(adminId, clock.instant());
        log.info("AI provider key removed by {}; AI turned off", adminId);
        return toView(repository.save(settings));
    }

    /**
     * Explicit, admin-requested connection test: one tiny generation call. Records a safe result.
     * Not transactional on purpose: the provider call can take minutes and must not hold a database connection.
     */
    public AiSettingsView testConnection(UUID adminId) {
        AiSettings settings = load();
        ProviderConnection connection = connectionFor(settings);
        try {
            adapters.complete(connection, new AiRequest("Reply with the single word OK.", "Connection test", false, 16));
            settings.recordTest(AiConnectionStatus.CONNECTED, "Connected successfully.", clock.instant());
        } catch (AiException e) {
            settings.recordTest(AiConnectionStatus.fromError(e.code()), e.code().message(), clock.instant());
        }
        log.info("AI connection test by {} for provider {}: {}", adminId, settings.getProvider(), settings.getLastTestStatus());
        return toView(repository.save(settings));
    }

    /** Model discovery for the configured provider (admin request only). Not transactional: it calls the provider. */
    public List<String> listModels() {
        return adapters.listModels(connectionFor(load()));
    }

    /** The connection used by AI features; refuses when AI is off or incomplete. */
    @Transactional(readOnly = true)
    public ProviderConnection activeConnection() {
        AiSettings settings = load();
        if (!settings.isEnabled()) {
            throw new AiException(AiErrorCode.AI_DISABLED);
        }
        return connectionFor(settings);
    }

    @Transactional(readOnly = true)
    public int dailyRequestLimitPerUser() {
        return load().getDailyRequestLimitPerUser();
    }

    @Transactional(readOnly = true)
    public int maxOutputTokens() {
        return load().getMaxOutputTokens();
    }

    private ProviderConnection connectionFor(AiSettings settings) {
        if (settings.getProvider() == null || settings.getModel() == null) {
            throw new AiException(AiErrorCode.NOT_CONFIGURED);
        }
        if (!settings.hasKey()) {
            throw new AiException(AiErrorCode.KEY_MISSING);
        }
        String baseUrl = settings.getProvider().requiresCustomBaseUrl()
                ? settings.getBaseUrl() : settings.getProvider().defaultBaseUrl();
        return new ProviderConnection(settings.getProvider(), baseUrl, cipher.decrypt(settings.encryptedKey()),
                settings.getModel(), Duration.ofSeconds(settings.getTimeoutSeconds()));
    }

    private AiSettings load() {
        return repository.findById(AiSettings.SINGLETON_ID).orElseGet(AiSettings::defaults);
    }

    private String resolveBaseUrl(AiProviderType provider, String requested) {
        if (provider == null || !provider.requiresCustomBaseUrl()) {
            return null; // built-in providers are pinned to their official endpoint
        }
        String url = blankToNull(requested);
        if (url == null) {
            throw new IllegalArgumentException("Enter the base URL of your OpenAI-compatible endpoint.");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The base URL isn't a valid URL.");
        }
        endpointGuard.requireAllowed(uri);
        return url;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AiSettingsView toView(AiSettings settings) {
        boolean keyUnreadable = settings.hasKey() && !cipher.canDecrypt(settings.encryptedKey());
        AiConnectionStatus status;
        if (keyUnreadable) {
            status = AiConnectionStatus.KEY_UNREADABLE;
        } else if (settings.getLastTestStatus() != null) {
            status = settings.getLastTestStatus();
        } else if (!settings.isEnabled()) {
            status = AiConnectionStatus.AI_DISABLED;
        } else if (settings.getProvider() == null || settings.getModel() == null) {
            status = AiConnectionStatus.NOT_CONFIGURED;
        } else if (!settings.hasKey()) {
            status = AiConnectionStatus.KEY_MISSING;
        } else {
            status = AiConnectionStatus.NOT_TESTED;
        }
        return new AiSettingsView(
                settings.isEnabled(),
                settings.getProvider(),
                settings.getProvider() == null ? null : settings.getProvider().displayName(),
                settings.getModel(),
                settings.getBaseUrl(),
                settings.getTimeoutSeconds(),
                settings.getMaxOutputTokens(),
                settings.getDailyRequestLimitPerUser(),
                settings.hasKey(),
                settings.hasKey() ? "••••" + settings.getApiKeyLast4() : null,
                cipher.isConfigured(),
                status,
                settings.getLastTestAt(),
                settings.getLastTestMessage(),
                settings.getUpdatedAt(),
                keyUnreadable);
    }
}
