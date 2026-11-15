package com.growthprint.backend.ai;

import com.growthprint.backend.ai.settings.AiCredentialCipher;
import com.growthprint.backend.ai.settings.AiSettings;
import com.growthprint.backend.ai.settings.AiSettingsRepository;
import com.growthprint.backend.ai.settings.AiStartupCheck;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The encryption key must fail loudly in production and never silently. */
class AiStartupCheckTest {

    private static AiStartupCheck check(String key, boolean prod) {
        AiSettingsRepository repository = mock(AiSettingsRepository.class);
        when(repository.findById(AiSettings.SINGLETON_ID)).thenReturn(Optional.of(AiSettings.defaults()));
        MockEnvironment env = new MockEnvironment();
        if (prod) {
            env.setActiveProfiles("prod");
        }
        return new AiStartupCheck(new AiCredentialCipher(key), repository, env);
    }

    @Test
    void aMalformedKeyStopsProductionButOnlyWarnsElsewhere() {
        assertThatThrownBy(() -> check("not-base64-and-not-32-bytes", true).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
        assertThatCode(() -> check("not-base64-and-not-32-bytes", false).run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void aMissingKeyIsFineWhileAiIsOff() {
        assertThatCode(() -> check("", true).run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void aValidKeyStartsNormally() {
        assertThatCode(() -> check(AiCredentialCipherTest.randomKey(), true).run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void theCipherDistinguishesNotSetFromMalformed() {
        org.assertj.core.api.Assertions.assertThat(new AiCredentialCipher("").keyStatus())
                .isEqualTo(AiCredentialCipher.KeyStatus.NOT_SET);
        org.assertj.core.api.Assertions.assertThat(new AiCredentialCipher("abc").keyStatus())
                .isEqualTo(AiCredentialCipher.KeyStatus.INVALID);
        org.assertj.core.api.Assertions.assertThat(new AiCredentialCipher(AiCredentialCipherTest.randomKey()).keyStatus())
                .isEqualTo(AiCredentialCipher.KeyStatus.CONFIGURED);
    }
}
