package com.growthprint.backend.invoice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Disabled("Requires Docker - excluded from default test run. Run with -Dtest=InvoiceLineItemTriggerIntegrationTest to enable.")
class InvoiceLineItemTriggerIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("growthprint")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideDatabaseProperties(DynamicPropertyRegistry registry) {
        // Explicitly override datasource properties to ensure Testcontainers is used
        // This prevents fallback to environment variables or .env file (Render database)
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationV14Applied() throws SQLException {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '14'",
                Integer.class);
        assertEquals(1, count, "V14 migration should be recorded");
    }

    @Test
    void triggerFunctionExists() throws SQLException {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'validate_line_item_amount')",
                Boolean.class);
        assertTrue(exists, "Trigger function validate_line_item_amount should exist");
    }

    @Test
    void triggerInsertExists() throws SQLException {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trigger_validate_line_item_amount_insert')",
                Boolean.class);
        assertTrue(exists, "INSERT trigger should exist");
    }

    @Test
    void triggerUpdateExists() throws SQLException {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trigger_validate_line_item_amount_update')",
                Boolean.class);
        assertTrue(exists, "UPDATE trigger should exist");
    }

    @Test
    void validLineItemInsertAccepted() {
        // First create an invoice to satisfy foreign key constraint
        UUID invoiceId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, invoice_number, client_id, client_name, client_email, issue_date, due_date, subtotal, tax_percent, tax_amount, total_amount, currency, status, created_at, updated_at) " +
                        "VALUES (?, 'INV-TEST-001', ?, 'Test Client', 'test@example.com', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', 100.00, 0.00, 0.00, 100.00, 'USD', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                invoiceId, clientId
        );

        // Test valid line item: 2 × 50.00 = 100.00
        UUID lineItemId = UUID.randomUUID();
        assertDoesNotThrow(() -> {
            jdbcTemplate.update(
                    "INSERT INTO invoice_line_items (id, invoice_id, description, quantity, unit_price, amount, created_at, updated_at) " +
                            "VALUES (?, ?, 'Test Item', 2, 50.00, 100.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    lineItemId, invoiceId
            );
        }, "Valid line item should be accepted");

        // Cleanup
        jdbcTemplate.update("DELETE FROM invoice_line_items WHERE id = ?", lineItemId);
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", invoiceId);
    }

    @Test
    void invalidLineItemInsertRejected() {
        // First create an invoice
        UUID invoiceId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, invoice_number, client_id, client_name, client_email, issue_date, due_date, subtotal, tax_percent, tax_amount, total_amount, currency, status, created_at, updated_at) " +
                        "VALUES (?, 'INV-TEST-002', ?, 'Test Client', 'test@example.com', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', 100.00, 0.00, 0.00, 100.00, 'USD', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                invoiceId, clientId
        );

        // Test invalid line item: 2 × 50.00 ≠ 150.00
        UUID lineItemId = UUID.randomUUID();
        Exception exception = assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO invoice_line_items (id, invoice_id, description, quantity, unit_price, amount, created_at, updated_at) " +
                            "VALUES (?, ?, 'Test Item', 2, 50.00, 150.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    lineItemId, invoiceId
            );
        });

        // Verify the error message mentions the calculation
        assertTrue(exception.getMessage().contains("does not match"),
                "Error should mention calculation mismatch");

        // Cleanup
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", invoiceId);
    }

    @Test
    void validLineItemUpdateAccepted() {
        // Create invoice and line item
        UUID invoiceId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, invoice_number, client_id, client_name, client_email, issue_date, due_date, subtotal, tax_percent, tax_amount, total_amount, currency, status, created_at, updated_at) " +
                        "VALUES (?, 'INV-TEST-003', ?, 'Test Client', 'test@example.com', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', 100.00, 0.00, 0.00, 100.00, 'USD', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                invoiceId, clientId
        );

        UUID lineItemId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoice_line_items (id, invoice_id, description, quantity, unit_price, amount, created_at, updated_at) " +
                        "VALUES (?, ?, 'Test Item', 2, 50.00, 100.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                lineItemId, invoiceId
        );

        // Update to valid amount: 3 × 50.00 = 150.00
        assertDoesNotThrow(() -> {
            jdbcTemplate.update(
                    "UPDATE invoice_line_items SET quantity = 3, unit_price = 50.00, amount = 150.00, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    lineItemId
            );
        }, "Valid line item update should be accepted");

        // Cleanup
        jdbcTemplate.update("DELETE FROM invoice_line_items WHERE id = ?", lineItemId);
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", invoiceId);
    }

    @Test
    void invalidLineItemUpdateRejected() {
        // Create invoice and line item
        UUID invoiceId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, invoice_number, client_id, client_name, client_email, issue_date, due_date, subtotal, tax_percent, tax_amount, total_amount, currency, status, created_at, updated_at) " +
                        "VALUES (?, 'INV-TEST-004', ?, 'Test Client', 'test@example.com', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', 100.00, 0.00, 0.00, 100.00, 'USD', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                invoiceId, clientId
        );

        UUID lineItemId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoice_line_items (id, invoice_id, description, quantity, unit_price, amount, created_at, updated_at) " +
                        "VALUES (?, ?, 'Test Item', 2, 50.00, 100.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                lineItemId, invoiceId
        );

        // Update to invalid amount: 3 × 50.00 ≠ 200.00
        Exception exception = assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "UPDATE invoice_line_items SET quantity = 3, unit_price = 50.00, amount = 200.00, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    lineItemId
            );
        });

        // Verify the error message mentions the calculation
        assertTrue(exception.getMessage().contains("does not match"),
                "Error should mention calculation mismatch");

        // Cleanup
        jdbcTemplate.update("DELETE FROM invoice_line_items WHERE id = ?", lineItemId);
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", invoiceId);
    }

    @Test
    void roundingEdgeCaseWithHalfUp() {
        // Test PostgreSQL ROUND() behavior with 3 × 33.33 = 99.99
        // PostgreSQL ROUND(3 * 33.33, 2) should round based on standard rounding rules
        UUID invoiceId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, invoice_number, client_id, client_name, client_email, issue_date, due_date, subtotal, tax_percent, tax_amount, total_amount, currency, status, created_at, updated_at) " +
                        "VALUES (?, 'INV-TEST-005', ?, 'Test Client', 'test@example.com', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', 99.99, 0.00, 0.00, 99.99, 'USD', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                invoiceId, clientId
        );

        UUID lineItemId = UUID.randomUUID();
        assertDoesNotThrow(() -> {
            jdbcTemplate.update(
                    "INSERT INTO invoice_line_items (id, invoice_id, description, quantity, unit_price, amount, created_at, updated_at) " +
                            "VALUES (?, ?, 'Test Item', 3, 33.33, 99.99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    lineItemId, invoiceId
            );
        }, "Line item with rounding edge case should be accepted if amount matches ROUND()");

        // Cleanup
        jdbcTemplate.update(" DELETE FROM invoice_line_items WHERE id = ?", lineItemId);
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", invoiceId);
    }

    @Test
    void roundingEdgeCaseRejectedWhenMismatched() {
        // Test that amount not matching ROUND() is rejected
        UUID invoiceId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, invoice_number, client_id, client_name, client_email, issue_date, due_date, subtotal, tax_percent, tax_amount, total_amount, currency, status, created_at, updated_at) " +
                        "VALUES (?, 'INV-TEST-006', ?, 'Test Client', 'test@example.com', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', 100.00, 0.00, 0.00, 100.00, 'USD', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                invoiceId, clientId
        );

        UUID lineItemId = UUID.randomUUID();
        Exception exception = assertThrows(Exception.class, () -> {
            // 3 × 33.33 = 99.99 with ROUND(..., 2), but we provide 100.00
            jdbcTemplate.update(
                    "INSERT INTO invoice_line_items (id, invoice_id, description, quantity, unit_price, amount, created_at, updated_at) " +
                            "VALUES (?, ?, 'Test Item', 3, 33.33, 100.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    lineItemId, invoiceId
            );
        });

        assertTrue(exception.getMessage().contains("does not match"),
                "Mismatched rounded amount should be rejected");

        // Cleanup
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", invoiceId);
    }

    @Test
    void allMigrationsAppliedIncludingV14() throws SQLException {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class
        );
        // Should have V1 through V14 = 14 migrations
        assertEquals(14, count, "All migrations including V14 should be applied");
    }
}
