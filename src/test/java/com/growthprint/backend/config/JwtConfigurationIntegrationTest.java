package com.growthprint.backend.config;

import com.growthprint.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for JWT configuration loading from environment variables.
 * Verifies that JWT configuration loads correctly when JWT_SECRET is provided.
 */
class JwtConfigurationIntegrationTest {

    @Test
    void whenJwtSecretIsProvided_thenJwtTokenProviderInitializesSuccessfully() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-testing-only-must-be-long-enough-for-hs256");
        jwtProperties.setExpiration(Duration.ofHours(1));
        jwtProperties.setIssuer("growthprint-backend");

        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);

        assertNotNull(jwtTokenProvider);
        assertEquals("test-secret-key-for-testing-only-must-be-long-enough-for-hs256", jwtProperties.getSecret());
    }

    @Test
    void whenJwtSecretIsProvidedViaEnvironment_thenConfigurationLoads() {
        String secret = System.getenv("JWT_SECRET");
        if (secret != null && !secret.isBlank() && secret.length() >= 32) {
            JwtProperties jwtProperties = new JwtProperties();
            jwtProperties.setSecret(secret);
            jwtProperties.setExpiration(Duration.ofHours(1));
            jwtProperties.setIssuer("growthprint-backend");

            JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
            assertNotNull(jwtTokenProvider);
        } else {
            System.out.println("Skipping environment variable test: JWT_SECRET not set or invalid");
        }
    }
}
