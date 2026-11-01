package com.growthprint.backend.testsupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full application context + MockMvc against an isolated PostgreSQL schema.
 * Test classes must also extend {@link AbstractPostgresIntegrationTest} (it registers the datasource).
 * Run only these tests with: {@code mvnw test -Dgroups=postgres}
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("postgres")
@ExtendWith(PostgresAvailableCondition.class)
@SpringBootTest
@AutoConfigureMockMvc
public @interface PostgresIntegrationTest {
}
