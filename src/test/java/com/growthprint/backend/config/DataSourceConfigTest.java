package com.growthprint.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class DataSourceConfigTest {

    private final DataSourceConfig dataSourceConfig = new DataSourceConfig();

    @Test
    void normalizeDatabaseUrl_convertsPostgresToJdbc() {
        String url = "postgres://localhost:5432/mydb";
        String normalized = dataSourceConfig.normalizeDatabaseUrl(url);
        assertTrue(normalized.startsWith("jdbc:postgresql://"));
    }

    @Test
    void normalizeDatabaseUrl_convertsPostgresqlToJdbc() {
        String url = "postgresql://localhost:5432/mydb";
        String normalized = dataSourceConfig.normalizeDatabaseUrl(url);
        assertTrue(normalized.startsWith("jdbc:postgresql://"));
    }

    @Test
    void normalizeDatabaseUrl_preservesJdbcPostgresql() {
        String url = "jdbc:postgresql://localhost:5432/mydb";
        String normalized = dataSourceConfig.normalizeDatabaseUrl(url);
        assertEquals(url, normalized);
    }

    @Test
    void normalizeDatabaseUrl_addsSslModeForRender() {
        String url = "jdbc:postgresql://dpg-test.render.com/mydb";
        String normalized = dataSourceConfig.normalizeDatabaseUrl(url);
        assertTrue(normalized.contains("sslmode=require"));
    }

    @Test
    void normalizeDatabaseUrl_addsSslModeForRenderWithExistingParams() {
        String url = "jdbc:postgresql://dpg-test.render.com/mydb?application_name=growthprint";
        String normalized = dataSourceConfig.normalizeDatabaseUrl(url);
        assertTrue(normalized.contains("sslmode=require"));
        assertTrue(normalized.contains("application_name=growthprint"));
    }

    @Test
    void hasEmbeddedCredentials_detectsEmbeddedCredentials() {
        String url = "jdbc:postgresql://user:pass@localhost:5432/mydb";
        assertTrue(dataSourceConfig.hasEmbeddedCredentials(url));
    }

    @Test
    void hasEmbeddedCredentials_returnsFalseForNoCredentials() {
        String url = "jdbc:postgresql://localhost:5432/mydb";
        assertFalse(dataSourceConfig.hasEmbeddedCredentials(url));
    }

    @Test
    void hasEmbeddedCredentials_returnsFalseForColonInPath() {
        String url = "jdbc:postgresql://localhost:5432/mydb:something";
        assertFalse(dataSourceConfig.hasEmbeddedCredentials(url));
    }

    // Test the private methods via reflection or by testing the overall behavior
    @Test
    void normalizeDatabaseUrl_isPublicForTesting() {
        // This test verifies the method is accessible for testing
        String url = "postgres://user:pass@dpg-test.render.com/mydb";
        String normalized = dataSourceConfig.normalizeDatabaseUrl(url);
        assertTrue(normalized.startsWith("jdbc:postgresql://"));
        assertTrue(normalized.contains("sslmode=require"));
    }
}
