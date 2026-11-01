package com.growthprint.backend.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Points the whole application at the isolated per-run schema from {@link PostgresTestDatabase}.
 * <p>
 * {@code DATABASE_URL}/{@code _USERNAME}/{@code _PASSWORD} are overridden on purpose: the project's
 * {@code .env} (loaded by spring-dotenv) points at the shared Render database and
 * {@code DataSourceConfig} builds the primary DataSource from {@code DATABASE_URL}. Dynamic properties
 * have the highest precedence, so the .env value can never be used by these tests.
 */
public abstract class AbstractPostgresIntegrationTest {

    /** Long random test-only secret; never used outside the test JVM. */
    protected static final String TEST_JWT_SECRET =
            "it-only-" + java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID();

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        if (!PostgresTestDatabase.isAvailable()) {
            return; // class is skipped by PostgresAvailableCondition
        }
        PostgresTestDatabase db = PostgresTestDatabase.get();
        registry.add("DATABASE_URL", db::jdbcUrl);
        registry.add("DATABASE_USERNAME", db::username);
        registry.add("DATABASE_PASSWORD", db::password);
        registry.add("spring.datasource.url", db::jdbcUrl);
        registry.add("spring.datasource.username", db::username);
        registry.add("spring.datasource.password", db::password);
        registry.add("spring.flyway.schemas", db::schema);
        registry.add("spring.flyway.default-schema", db::schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", db::schema);
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
        // Keep rate limits out of the way of workflow tests (dedicated unit tests cover limiting).
        registry.add("app.rate-limit.login.max-requests", () -> "1000");
        registry.add("app.rate-limit.public-inquiries.max-requests", () -> "1000");
    }
}
