package com.growthprint.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@Disabled("Requires Docker - excluded from default test run. Run with -Dtest=PostgreSQLIntegrationTest to enable.")
class PostgreSQLIntegrationTest {

	@Container
	@ServiceConnection
	// Resource leak warning suppressed: Testcontainers @Container annotation
	// manages lifecycle automatically
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("growthprint")
			.withUsername("testuser")
			.withPassword("testpass");

	@Test
	void applicationContextLoadsWithPostgreSQL() {
		// If this test passes, Spring Boot started successfully with PostgreSQL
		assertTrue(true, "Application context loaded with PostgreSQL");
	}
}
