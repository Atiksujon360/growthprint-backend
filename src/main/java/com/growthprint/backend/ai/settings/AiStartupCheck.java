package com.growthprint.backend.ai.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Start-up check of the AI encryption key, so a misconfiguration is loud instead of silent.
 * <ul>
 *     <li>A key value that is not base64 of exactly 32 bytes always counts as a configuration error.</li>
 *     <li>In the {@code prod} profile the application refuses to start when it is malformed, or when AI is switched on
 *     in the database but the key is missing or can no longer decrypt the stored API key.</li>
 *     <li>Elsewhere the same problems are logged as warnings.</li>
 * </ul>
 * AI is optional: with no key and AI off, the application starts normally and logs one informational line.
 */
@Component
public class AiStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiStartupCheck.class);

    private final AiCredentialCipher cipher;
    private final AiSettingsRepository repository;
    private final Environment environment;

    public AiStartupCheck(AiCredentialCipher cipher, AiSettingsRepository repository, Environment environment) {
        this.cipher = cipher;
        this.repository = repository;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        AiSettings settings = repository.findById(AiSettings.SINGLETON_ID).orElse(null);
        boolean aiOn = settings != null && settings.isEnabled();

        switch (cipher.keyStatus()) {
            case INVALID -> problem(prod, "AI_CREDENTIALS_ENCRYPTION_KEY is set but is not base64 of exactly 32 bytes. "
                    + "Generate one with: openssl rand -base64 32");
            case NOT_SET -> {
                if (aiOn) {
                    problem(prod, "AI is switched on but AI_CREDENTIALS_ENCRYPTION_KEY is not set, so the saved API key "
                            + "can't be read.");
                } else {
                    log.info("AI_CREDENTIALS_ENCRYPTION_KEY is not set: AI provider keys can't be saved until it is.");
                }
            }
            case CONFIGURED -> {
                if (settings != null && settings.hasKey() && !cipher.canDecrypt(settings.encryptedKey())) {
                    problem(prod && aiOn, "The saved AI API key can't be decrypted with the current "
                            + "AI_CREDENTIALS_ENCRYPTION_KEY (was it changed?). Enter the API key again under AI providers.");
                }
            }
        }
    }

    private void problem(boolean fatal, String message) {
        if (fatal) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
    }
}
