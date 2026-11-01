package com.growthprint.backend.ai.settings;

import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts AI provider API keys at rest with AES-256-GCM (authenticated encryption, random 96-bit IV per
 * value). The encryption key lives outside the database, in the {@code AI_CREDENTIALS_ENCRYPTION_KEY}
 * environment variable (base64 of 32 random bytes, e.g. {@code openssl rand -base64 32}).
 * Without it, keys cannot be saved at all.
 */
@Component
public class AiCredentialCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    public record EncryptedSecret(String ciphertext, String iv) {
        @Override
        public String toString() {
            return "EncryptedSecret[****]";
        }
    }

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AiCredentialCipher(@Value("${app.ai.credentials-encryption-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    public boolean isConfigured() {
        return key != null;
    }

    public EncryptedSecret encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(Base64.getEncoder().encodeToString(encrypted), Base64.getEncoder().encodeToString(iv));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the API key");
        }
    }

    public String decrypt(EncryptedSecret secret) {
        requireKey();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(secret.iv())));
            return new String(cipher.doFinal(Base64.getDecoder().decode(secret.ciphertext())), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong encryption key (rotated?) or tampered value: the admin must enter the key again.
            throw new AiException(AiErrorCode.KEY_MISSING, e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new AiException(AiErrorCode.ENCRYPTION_KEY_MISSING);
        }
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Key.trim());
            return bytes.length == 32 ? new SecretKeySpec(bytes, "AES") : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
