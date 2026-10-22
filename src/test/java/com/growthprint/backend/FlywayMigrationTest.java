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
	// Resource leak warning suppressed: Testcontainers @Container annotation
	// manages lifecycle automatically
	@SuppressWarnings("resource")
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
	void allMigrationsApplied() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			ResultSet resultSet = connection.createStatement().executeQuery(
					"SELECT COUNT(*) FROM flyway_schema_history");
			assertTrue(resultSet.next(), "Query should return a result");
			int count = resultSet.getInt(1);
			assertEquals(4, count, "All 4 migrations (V1-V4) should be recorded");
		}
	}

	@Test
	void crmLeadsTableExists() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			ResultSet resultSet = connection.getMetaData().getTables(
					null, null, "crm_leads", null);
			assertTrue(resultSet.next(), "crm_leads table should exist");
		}
	}

	@Test
	void crmLeadsTableStructure() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			ResultSet resultSet = connection.getMetaData().getColumns(
					null, null, "crm_leads", null);
			java.util.Set<String> columns = new java.util.HashSet<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("COLUMN_NAME"));
			}
			// Verify all expected columns exist
			java.util.Set<String> expectedColumns = java.util.Set.of(
					"id", "company_name", "website", "industry", "location",
					"contact_name", "contact_email", "contact_title", "source",
					"source_url", "discovered_date", "target_service",
					"identified_problem", "research_summary", "qualification_score",
					"business_fit_score", "evidence_of_need_score", "contactability_score",
					"service_relevance_score", "pipeline_stage", "last_contact_date",
					"next_action", "do_not_contact", "notes", "inquiry_id",
					"created_at", "updated_at");
			assertTrue(columns.containsAll(expectedColumns),
					"crm_leads table should have all expected columns");
		}
	}

	@Test
	void crmLeadsConstraintsExist() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			// Check for foreign key constraint
			ResultSet fkResultSet = connection.getMetaData().getImportedKeys(
					null, null, "crm_leads");
			boolean hasFkToLeads = false;
			while (fkResultSet.next()) {
				if ("leads".equals(fkResultSet.getString("PKTABLE_NAME"))) {
					hasFkToLeads = true;
					break;
				}
			}
			assertTrue(hasFkToLeads, "crm_leads should have foreign key to leads table");
		}
	}

	@Test
	void dataSourceIsNotNull() {
		assertNotNull(dataSource, "DataSource should be configured");
	}
}
