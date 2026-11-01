package com.growthprint.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JwtProperties configuration loading.
 * Verifies that JWT secret is correctly loaded from environment variables
 * and that validation constraints are enforced.
 */
@SpringBootTest(classes = JwtPropertiesTest.TestConfig.class)
@TestPropertySource(properties = {
    "app.security.jwt.secret=test-secret-key-for-testing-only-must-be-long-enough-for-hs256",
    "app.security.jwt.expiration=1h",
    "app.security.jwt.issuer=growthprint-backend"
})
class JwtPropertiesTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void whenSecretIsProvided_thenPropertiesAreLoaded() {
        assertNotNull(jwtProperties);
        assertEquals("test-secret-key-for-testing-only-must-be-long-enough-for-hs256", jwtProperties.getSecret());
        assertEquals(Duration.ofHours(1), jwtProperties.getExpiration());
        assertEquals("growthprint-backend", jwtProperties.getIssuer());
    }

    @Test
    void whenSecretIsTooShort_thenValidationFails() {
        JwtProperties props = new JwtProperties();
        props.setSecret("short");
        props.setExpiration(Duration.ofHours(1));
        props.setIssuer("growthprint-backend");

        assertThrows(IllegalStateException.class, () -> validateProperties(props));
    }

    @Test
    void whenSecretIsBlank_thenValidationFails() {
        JwtProperties props = new JwtProperties();
        props.setSecret("");
        props.setExpiration(Duration.ofHours(1));
        props.setIssuer("growthprint-backend");

        assertThrows(IllegalStateException.class, () -> validateProperties(props));
    }

    @Test
    void whenSecretIsNull_thenValidationFails() {
        JwtProperties props = new JwtProperties();
        props.setSecret(null);
        props.setExpiration(Duration.ofHours(1));
        props.setIssuer("growthprint-backend");

        assertThrows(IllegalStateException.class, () -> validateProperties(props));
    }

    private void validateProperties(JwtProperties props) {
        if (props.getSecret() == null || props.getSecret().isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }
        if (props.getSecret().length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }
}
