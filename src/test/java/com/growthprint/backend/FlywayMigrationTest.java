package com.growthprint.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@Disabled("Requires Docker - excluded from default test run. Run with -Dtest=FlywayMigrationTest to enable.")
class FlywayMigrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("growthprint")
			.withUsername("testuser")
			.withPassword("testpass");

	@Autowired
	private DataSource dataSource;

	@Test
	void flywaySchemaHistoryTableExists() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			ResultSet resultSet = connection.getMetaData().getTables(
					null, null, "flyway_schema_history", null);
			assertTrue(resultSet.next(), "flyway_schema_history table should exist");
		}
	}

	@Test
	void flywayMigrationRecorded() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			ResultSet resultSet = connection.createStatement().executeQuery(
					"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'");
			assertTrue(resultSet.next(), "Query should return a result");
			int count = resultSet.getInt(1);
			assertEquals(1, count, "V1 migration should be recorded exactly once");
		}
	}

	@Test
	void dataSourceIsNotNull() {
		assertNotNull(dataSource, "DataSource should be configured");
	}
}
