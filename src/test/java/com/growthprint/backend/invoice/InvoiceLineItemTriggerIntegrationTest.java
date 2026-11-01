package com.growthprint.backend.invoice;

import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@PostgresIntegrationTest
class InvoiceLineItemTriggerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** invoices.client_id has a foreign key to clients, so insert a real client row first. */
    private UUID existingClientId() {
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO clients (id, name, legal_name, contact_email, website, status, contract_start_date, "
                        + "account_manager, billing_currency) VALUES (?, 'Trigger Test Client', 'Trigger Test Client LLC', "
                        + "'trigger@example.com', 'https://example.com', 'ACTIVE', CURRENT_DATE, 'IT', 'USD')",
                clientId);
        return clientId;
    }

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
                "SELECT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = current_schema() AND p.proname = 'validate_line_item_amount')",
                Boolean.class);
        assertTrue(exists, "Trigger function validate_line_item_amount should exist");
    }

    @Test
    void triggerInsertExists() throws SQLException {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = current_schema() AND t.tgname = 'trigger_validate_line_item_amount_insert')",
                Boolean.class);
        assertTrue(exists, "INSERT trigger should exist");
    }

    @Test
    void triggerUpdateExists() throws SQLException {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = current_schema() AND t.tgname = 'trigger_validate_line_item_amount_update')",
                Boolean.class);
        assertTrue(exists, "UPDATE trigger should exist");
    }

    @Test
    void validLineItemInsertAccepted() {
        // First create an invoice to satisfy foreign key constraint
        UUID invoiceId = UUID.randomUUID();
        UUID clientId = existingClientId();
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
        UUID clientId = existingClientId();
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
        UUID clientId = existingClientId();
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
        UUID clientId = existingClientId();
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
        UUID clientId = existingClientId();
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
        UUID clientId = existingClientId();
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
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '14' AND success",
                Integer.class
        );
        assertEquals(1, count, "V14 must be applied successfully (later migrations may follow)");
    }
}
