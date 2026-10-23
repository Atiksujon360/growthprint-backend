-- V8: Create invoices and invoice_line_items tables
-- This migration adds invoice management with line items.
--
-- INVOICE MANAGEMENT:
-- - invoices table stores invoice headers
-- - invoice_line_items table stores individual line items
-- - Foreign keys ensure referential integrity
-- - Monetary values use DECIMAL(12,2) for precision
-- - Status enum: DRAFT, ISSUED, PAID, OVERDUE, VOID
--
-- FINANCIAL SAFETY:
-- - No payment gateway fields or secrets
-- - No credit card or sensitive payment data
-- - Only status tracking and history
-- - Mark paid operation is status update only
--
-- RELATIONSHIPS:
-- - invoices.client_id → clients.id (ON DELETE RESTRICT)
-- - invoice_line_items.invoice_id → invoices.id (ON DELETE CASCADE)

CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(50) NOT NULL,
    client_id UUID NOT NULL,
    client_name VARCHAR(200) NOT NULL,
    client_email VARCHAR(200) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    paid_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_invoices_client FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS invoice_line_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL,
    description TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_line_items_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoices(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_invoice_number ON invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoices_client_id ON invoices(client_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON invoices(due_date);
CREATE INDEX IF NOT EXISTS idx_line_items_invoice_id ON invoice_line_items(invoice_id);

-- CHECK constraints
ALTER TABLE invoices
ADD CONSTRAINT chk_invoice_status
CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'OVERDUE', 'VOID'));

ALTER TABLE invoices
ADD CONSTRAINT chk_invoice_currency
CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD', 'AUD'));

ALTER TABLE invoices
ADD CONSTRAINT chk_invoice_tax_percent
CHECK (tax_percent >= 0 AND tax_percent <= 100);

ALTER TABLE invoices
ADD CONSTRAINT chk_invoice_amounts
CHECK (subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0);

ALTER TABLE invoice_line_items
ADD CONSTRAINT chk_line_item_quantity
CHECK (quantity > 0);

ALTER TABLE invoice_line_items
ADD CONSTRAINT chk_line_item_prices
CHECK (unit_price >= 0 AND amount >= 0);

-- Trigger function to update updated_at
CREATE OR REPLACE FUNCTION update_invoices_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_line_items_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers
DROP TRIGGER IF EXISTS trigger_update_invoices_updated_at ON invoices;
CREATE TRIGGER trigger_update_invoices_updated_at
    BEFORE UPDATE ON invoices
    FOR EACH ROW
    EXECUTE FUNCTION update_invoices_updated_at();

DROP TRIGGER IF EXISTS trigger_update_line_items_updated_at ON invoice_line_items;
CREATE TRIGGER trigger_update_line_items_updated_at
    BEFORE UPDATE ON invoice_line_items
    FOR EACH ROW
    EXECUTE FUNCTION update_line_items_updated_at();

-- Comments
COMMENT ON TABLE invoices IS 'Invoice headers with client information and totals';
COMMENT ON TABLE invoice_line_items IS 'Invoice line items with description, quantity, and pricing';
COMMENT ON COLUMN invoices.invoice_number IS 'Unique invoice number (e.g., INV-2026-0041)';
COMMENT ON COLUMN invoices.client_id IS 'Foreign key to clients table';
COMMENT ON COLUMN invoices.status IS 'Invoice status: DRAFT, ISSUED, PAID, OVERDUE, VOID';
COMMENT ON COLUMN invoices.paid_at IS 'Timestamp when invoice was marked as paid';
COMMENT ON COLUMN invoice_line_items.invoice_id IS 'Foreign key to invoices table';
