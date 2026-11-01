package com.growthprint.backend;

import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class PostgreSQLIntegrationTest extends AbstractPostgresIntegrationTest {

	@Test
	void applicationContextLoadsWithPostgreSQL() {
		// If this test passes, Spring Boot started successfully with PostgreSQL
		assertTrue(true, "Application context loaded with PostgreSQL");
	}
}
