package com.growthprint.backend.ai;

import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiException;
import com.growthprint.backend.ai.settings.AiCredentialCipher;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCredentialCipherTest {

    static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void encryptsWithAFreshIvEveryTimeAndDecryptsBack() {
        AiCredentialCipher cipher = new AiCredentialCipher(randomKey());
        AiCredentialCipher.EncryptedSecret first = cipher.encrypt("sk-live-secret");
        AiCredentialCipher.EncryptedSecret second = cipher.encrypt("sk-live-secret");

        assertThat(first.ciphertext()).doesNotContain("sk-live-secret");
        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(cipher.decrypt(first)).isEqualTo("sk-live-secret");
    }

    @Test
    void tamperedCiphertextIsRejected() {
        AiCredentialCipher cipher = new AiCredentialCipher(randomKey());
        AiCredentialCipher.EncryptedSecret secret = cipher.encrypt("sk-live-secret");
        byte[] bytes = Base64.getDecoder().decode(secret.ciphertext());
        bytes[0] ^= 0x01;
        AiCredentialCipher.EncryptedSecret tampered =
                new AiCredentialCipher.EncryptedSecret(Base64.getEncoder().encodeToString(bytes), secret.iv());

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(AiException.class);
    }

    @Test
    void aDifferentEncryptionKeyCannotDecrypt() {
        AiCredentialCipher.EncryptedSecret secret = new AiCredentialCipher(randomKey()).encrypt("sk-live-secret");
        assertThatThrownBy(() -> new AiCredentialCipher(randomKey()).decrypt(secret)).isInstanceOf(AiException.class);
    }

    @Test
    void missingEncryptionKeyRefusesToStoreSecrets() {
        AiCredentialCipher cipher = new AiCredentialCipher("");
        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("sk"))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.ENCRYPTION_KEY_MISSING));
    }

    @Test
    void wrongLengthEncryptionKeyIsTreatedAsNotConfigured() {
        assertThat(new AiCredentialCipher(Base64.getEncoder().encodeToString(new byte[16])).isConfigured()).isFalse();
        assertThat(new AiCredentialCipher("not base64 !!").isConfigured()).isFalse();
    }
}
