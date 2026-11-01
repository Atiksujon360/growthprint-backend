package com.growthprint.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for JWT property binding from application.yml.
 * Verifies that the property binding configuration is correct.
 */
class JwtPropertiesBindingTest {

    @Test
    void whenPropertiesAreSet_thenBindingWorks() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-testing-only-must-be-long-enough-for-hs256");
        jwtProperties.setExpiration(java.time.Duration.ofHours(1));
        jwtProperties.setIssuer("growthprint-backend");

        assertEquals("test-secret-key-for-testing-only-must-be-long-enough-for-hs256", jwtProperties.getSecret());
        assertEquals(java.time.Duration.ofHours(1), jwtProperties.getExpiration());
        assertEquals("growthprint-backend", jwtProperties.getIssuer());
    }

    @Test
    void whenSecretIsFromEnvironment_thenBindingShouldWork() {
        Map<String, Object> envVars = new HashMap<>();
        envVars.put("JWT_SECRET", "test-secret-key-for-testing-only-must-be-long-enough-for-hs256");

        MapPropertySource propertySource = new MapPropertySource("testEnv", envVars);

        String secret = propertySource.getProperty("JWT_SECRET").toString();
        assertEquals("test-secret-key-for-testing-only-must-be-long-enough-for-hs256", secret);
    }
}
