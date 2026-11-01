package com.growthprint.backend.testsupport;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

/**
 * Resolves an <b>isolated</b> PostgreSQL database for integration tests, once per JVM.
 * <ol>
 *     <li>{@code GP_IT_DATABASE_URL} / {@code GP_IT_DATABASE_USERNAME} / {@code GP_IT_DATABASE_PASSWORD}
 *     environment variables (intended: the Neon {@code growthprint-mvp-test} database), or</li>
 *     <li>a Testcontainers {@code postgres:16-alpine} container when Docker is available, or</li>
 *     <li>nothing: tests annotated with {@link PostgresIntegrationTest} are then skipped.</li>
 * </ol>
 * Every run uses a fresh, randomly named schema ({@code gp_it_xxxxxxxx}). All Flyway migrations are
 * applied into it and it is dropped when the JVM exits, so the shared test database is never polluted
 * and existing schemas are never touched.
 * <p>
 * Safety: Render hosts are refused outright. The project's {@code .env} points at the shared Render
 * database, so this class deliberately never reads {@code DATABASE_URL}.
 */
public final class PostgresTestDatabase {

    public static final String URL_ENV = "GP_IT_DATABASE_URL";
    public static final String USERNAME_ENV = "GP_IT_DATABASE_USERNAME";
    public static final String PASSWORD_ENV = "GP_IT_DATABASE_PASSWORD";

    private static volatile PostgresTestDatabase instance;
    private static volatile Boolean dockerAvailable;

    private final String baseJdbcUrl;
    private final String username;
    private final String password;
    private final String schema;
    private final String source;

    private PostgresTestDatabase(String baseJdbcUrl, String username, String password, String source) {
        this.baseJdbcUrl = baseJdbcUrl;
        this.username = username;
        this.password = password;
        this.source = source;
        this.schema = "gp_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** True when an external test database is configured or Docker can provide one. */
    public static boolean isAvailable() {
        return externalUrl() != null || isDockerAvailable();
    }

    public static synchronized PostgresTestDatabase get() {
        if (instance == null) {
            instance = create();
            instance.createSchema();
            PostgresTestDatabase created = instance;
            Runtime.getRuntime().addShutdownHook(new Thread(created::dropSchemaQuietly, "gp-it-schema-cleanup"));
        }
        return instance;
    }

    private static PostgresTestDatabase create() {
        String url = externalUrl();
        if (url != null) {
            String jdbcUrl = toJdbcUrl(url);
            assertNotShared(jdbcUrl);
            return new PostgresTestDatabase(jdbcUrl, System.getenv(USERNAME_ENV), System.getenv(PASSWORD_ENV),
                    "external (" + URL_ENV + ")");
        }
        if (isDockerAvailable()) {
            @SuppressWarnings("resource") // lifetime = JVM; Testcontainers' Ryuk removes it afterwards
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("growthprint_it")
                    .withUsername("it")
                    .withPassword("it");
            container.start();
            return new PostgresTestDatabase(container.getJdbcUrl(), container.getUsername(), container.getPassword(),
                    "testcontainers");
        }
        throw new IllegalStateException("No isolated PostgreSQL available: set " + URL_ENV + " or start Docker");
    }

    private static String externalUrl() {
        String url = System.getenv(URL_ENV);
        return url == null || url.isBlank() ? null : url.trim();
    }

    private static synchronized boolean isDockerAvailable() {
        if (dockerAvailable == null) {
            boolean available;
            try {
                available = DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable e) {
                available = false;
            }
            dockerAvailable = available;
        }
        return dockerAvailable;
    }

    /** Accepts postgres://, postgresql:// or jdbc:postgresql:// URLs. */
    static String toJdbcUrl(String url) {
        if (url.startsWith("jdbc:postgresql://")) {
            return url;
        }
        if (url.startsWith("postgresql://")) {
            return "jdbc:" + url;
        }
        if (url.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }
        throw new IllegalStateException(URL_ENV + " must be a PostgreSQL URL");
    }

    /** Hard stop: never run tests against the shared Render database. */
    static void assertNotShared(String jdbcUrl) {
        if (jdbcUrl.toLowerCase(Locale.ROOT).contains("render.com")) {
            throw new IllegalStateException(URL_ENV + " points at a Render host. Integration tests must only use "
                    + "the isolated test database (e.g. Neon growthprint-mvp-test).");
        }
    }

    /** JDBC URL whose search_path is the per-run schema. Use a direct (non-pooled) Neon endpoint. */
    public String jdbcUrl() {
        String separator = baseJdbcUrl.contains("?") ? "&" : "?";
        return baseJdbcUrl + separator + "currentSchema=" + schema;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String schema() {
        return schema;
    }

    public String source() {
        return source;
    }

    private void createSchema() {
        execute("CREATE SCHEMA " + schema);
    }

    private void dropSchemaQuietly() {
        try {
            execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } catch (RuntimeException ignored) {
            // Best effort; the name is random and prefixed gp_it_ so leftovers are easy to find.
        }
    }

    private void execute(String sql) {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute '" + sql + "' on the integration test database", e);
        }
    }
}
