package com.growthprint.backend.ai.settings;

import com.growthprint.backend.ai.provider.AiErrorCode;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-managed AI configuration: one active provider, model, limits and an encrypted API key.
 * <ul>
 *     <li>AI is off by default and can only be turned on once provider, model and key are set.</li>
 *     <li>The key is write-only: it is encrypted before saving and only ever returned masked.</li>
 *     <li>Built-in providers always use their official endpoint; only the custom OpenAI-compatible provider
 *     takes an admin-supplied URL, which must be HTTPS (plain HTTP is allowed for localhost only).</li>
 *     <li>A connection test runs only when the admin asks for it.</li>
 * </ul>
 */
@Service
public class AiSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AiSettingsService.class);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final AiSettingsRepository repository;
    private final AiCredentialCipher cipher;
    private final AiProviderAdapters adapters;
    private final Clock clock;

    public AiSettingsService(AiSettingsRepository repository, AiCredentialCipher cipher,
                             AiProviderAdapters adapters, Clock clock) {
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
        boolean connectionChanged = providerChanged
                || !java.util.Objects.equals(settings.getModel(), model)
                || !java.util.Objects.equals(settings.getBaseUrl(), baseUrl);

        if (providerChanged && settings.hasKey()) {
            // A key belongs to one provider; never send it to a different one.
            settings.removeKey(adminId, clock.instant());
        }

        if (command.enabled()) {
            if (provider == null || model == null) {
                throw new AiException(AiErrorCode.NOT_CONFIGURED);
            }
            if (!settings.hasKey()) {
                throw new AiException(AiErrorCode.KEY_MISSING);
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

    /** Explicit, admin-requested connection test: one tiny generation call. Records a safe result. */
    @Transactional
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

    /** Model discovery for the configured provider (admin request only). */
    @Transactional(readOnly = true)
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

    private static String resolveBaseUrl(AiProviderType provider, String requested) {
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
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean local = LOCAL_HOSTS.contains(host);
        if (host.isEmpty() || uri.getUserInfo() != null || uri.getQuery() != null
                || !(scheme.equals("https") || (scheme.equals("http") && local))) {
            throw new IllegalArgumentException("The base URL must use https:// (plain http is only allowed for localhost).");
        }
        return url;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AiSettingsView toView(AiSettings settings) {
        AiConnectionStatus status;
        if (settings.getLastTestStatus() != null) {
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
                settings.getUpdatedAt());
    }
}
