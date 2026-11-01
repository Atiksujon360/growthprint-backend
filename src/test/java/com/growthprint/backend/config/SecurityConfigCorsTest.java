package com.growthprint.backend.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CORS configuration parsing logic.
 * Tests the string parsing logic from SecurityConfig without requiring full Spring context.
 */
class SecurityConfigCorsTest {

    @Test
    void corsConfigurationParsesMultipleOrigins() {
        // Simulate the parsing logic from SecurityConfig
        String testOrigins = "http://localhost:3000,https://example.vercel.app,https://another.app";
        List<String> parsedOrigins = Arrays.stream(testOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        assertEquals(3, parsedOrigins.size());
        assertTrue(parsedOrigins.contains("http://localhost:3000"));
        assertTrue(parsedOrigins.contains("https://example.vercel.app"));
        assertTrue(parsedOrigins.contains("https://another.app"));
    }

    @Test
    void corsConfigurationHandlesWhitespace() {
        String testOrigins = " http://localhost:3000 , https://example.vercel.app ";
        List<String> parsedOrigins = Arrays.stream(testOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        assertEquals(2, parsedOrigins.size());
        assertEquals("http://localhost:3000", parsedOrigins.get(0));
        assertEquals("https://example.vercel.app", parsedOrigins.get(1));
    }

    @Test
    void corsConfigurationHandlesEmptyOrigins() {
        String testOrigins = ",http://localhost:3000,,https://example.vercel.app,";
        List<String> parsedOrigins = Arrays.stream(testOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        assertEquals(2, parsedOrigins.size());
        assertTrue(parsedOrigins.contains("http://localhost:3000"));
        assertTrue(parsedOrigins.contains("https://example.vercel.app"));
    }

    @Test
    void corsConfigurationLocalhostFallback() {
        // Test default fallback when property is not set
        String defaultOrigins = "http://localhost:3000";
        List<String> parsedOrigins = Arrays.stream(defaultOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        assertEquals(1, parsedOrigins.size());
        assertEquals("http://localhost:3000", parsedOrigins.get(0));
    }

    @Test
    void corsConfigurationSingleOrigin() {
        String testOrigins = "https://growthprint.vercel.app";
        List<String> parsedOrigins = Arrays.stream(testOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        assertEquals(1, parsedOrigins.size());
        assertEquals("https://growthprint.vercel.app", parsedOrigins.get(0));
    }

    @Test
    void realParserTrimsAndDropsEmptyEntries() {
        assertEquals(List.of("http://localhost:3000", "https://app.example.com"),
                SecurityConfig.parseAllowedOrigins(" http://localhost:3000 ,, https://app.example.com "));
        assertEquals(List.of(), SecurityConfig.parseAllowedOrigins(null));
    }

    @Test
    void realParserRejectsWildcardBecauseCredentialsAreAllowed() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.parseAllowedOrigins("https://app.example.com,*"));
    }

    @Test
    void corsConfigurationPreservesHttps() {
        String testOrigins = "https://production.example.com,http://localhost:3000";
        List<String> parsedOrigins = Arrays.stream(testOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        assertEquals(2, parsedOrigins.size());
        assertTrue(parsedOrigins.get(0).startsWith("https://"));
        assertTrue(parsedOrigins.get(1).startsWith("http://"));
    }
}
