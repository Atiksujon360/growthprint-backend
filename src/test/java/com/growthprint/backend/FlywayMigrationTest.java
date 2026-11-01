package com.growthprint.backend;

import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies every Flyway migration (V1..latest) to a fresh, isolated PostgreSQL schema and verifies the result.
 * All metadata queries are scoped to {@code current_schema()} so tables in other schemas cannot mask failures.
 */
@PostgresIntegrationTest
class FlywayMigrationTest extends AbstractPostgresIntegrationTest {

	private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void everyMigrationOnTheClasspathIsAppliedSuccessfully() throws IOException {
		Set<String> expectedVersions = migrationVersionsOnClasspath();

		List<String> applied = jdbcTemplate.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL", String.class);
		Integer failed = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class);

		assertThat(new HashSet<>(applied)).isEqualTo(expectedVersions);
		assertThat(failed).isZero();
	}

	@Test
	void allCoreTablesExistInTheMigratedSchema() {
		for (String table : List.of("leads", "clients", "crm_leads", "users", "projects", "project_milestones",
				"invoices", "invoice_line_items", "outreach_drafts", "support_requests", "support_estimates",
				"proposals")) {
			assertThat(tableExists(table)).as("table %s", table).isTrue();
		}
	}

	@Test
	void crmLeadsTableStructure() {
		List<String> columns = jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns "
						+ "WHERE table_schema = current_schema() AND table_name = 'crm_leads'", String.class);

		assertThat(columns).contains(
				"id", "company_name", "website", "industry", "location",
				"contact_name", "contact_email", "contact_title", "source",
				"source_url", "discovered_date", "target_service",
				"identified_problem", "research_summary", "qualification_score",
				"business_fit_score", "evidence_of_need_score", "contactability_score",
				"service_relevance_score", "pipeline_stage", "last_contact_date",
				"next_action", "do_not_contact", "notes", "inquiry_id",
				"created_at", "updated_at");
	}

	@Test
	void crmLeadsHasForeignKeyToLeads() {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.table_constraints tc "
						+ "JOIN information_schema.constraint_column_usage ccu "
						+ "  ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema "
						+ "WHERE tc.table_schema = current_schema() AND tc.table_name = 'crm_leads' "
						+ "  AND tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'leads'",
				Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void v14LineItemIntegrityTriggersExist() {
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid "
						+ "JOIN pg_namespace n ON n.oid = c.relnamespace "
						+ "WHERE n.nspname = current_schema() AND c.relname = 'invoice_line_items' "
						+ "AND t.tgname IN ('trigger_validate_line_item_amount_insert', "
						+ "'trigger_validate_line_item_amount_update')",
				Integer.class)).isEqualTo(2);
	}

	private boolean tableExists(String table) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
				Integer.class, table);
		return count != null && count == 1;
	}

	private static Set<String> migrationVersionsOnClasspath() throws IOException {
		Resource[] resources = new PathMatchingResourcePatternResolver()
				.getResources("classpath*:db/migration/V*__*.sql");
		Set<String> versions = new HashSet<>();
		Arrays.stream(resources).forEach(resource -> {
			Matcher matcher = VERSION.matcher(resource.getFilename() == null ? "" : resource.getFilename());
			if (matcher.matches()) {
				versions.add(matcher.group(1));
			}
		});
		assertThat(versions).as("migration files found on classpath").isNotEmpty();
		return versions;
	}
}
