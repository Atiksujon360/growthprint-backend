-- V15: Client-reported invoice payments, verified or rejected by an ADMIN.
--
-- A CLIENT can report "I paid this invoice" but can never mark an invoice PAID. Each report is an append-only
-- record (never deleted; only its review fields change once). Verifying a report is the authorised admin action
-- that marks the invoice PAID (same rule as POST /api/v1/invoices/{id}/reconcile).
-- New table only: no existing data is modified.

CREATE TABLE IF NOT EXISTS invoice_payment_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL,
    client_id UUID NOT NULL,
    reported_by_user_id UUID NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    reference VARCHAR(120),
    paid_on DATE NOT NULL,
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_reports_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_reports_client FOREIGN KEY (client_id)
        REFERENCES clients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_reports_reported_by FOREIGN KEY (reported_by_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_reports_reviewed_by FOREIGN KEY (reviewed_by_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_payment_reports_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_reports_currency CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD', 'AUD')),
    CONSTRAINT chk_payment_reports_method CHECK (payment_method IN ('BANK_TRANSFER', 'CARD', 'CHECK', 'OTHER')),
    CONSTRAINT chk_payment_reports_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    -- A reviewed report always records who reviewed it and when; a pending one never does.
    CONSTRAINT chk_payment_reports_review CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
        OR (status <> 'PENDING' AND reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_payment_reports_invoice_id ON invoice_payment_reports(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_reports_client_id ON invoice_payment_reports(client_id);
CREATE INDEX IF NOT EXISTS idx_payment_reports_status ON invoice_payment_reports(status);

-- At most one report awaiting review per invoice (prevents duplicate submissions / double clicks).
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_reports_one_pending_per_invoice
    ON invoice_payment_reports(invoice_id) WHERE status = 'PENDING';

CREATE OR REPLACE FUNCTION update_invoice_payment_reports_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_invoice_payment_reports_updated_at ON invoice_payment_reports;
CREATE TRIGGER trigger_update_invoice_payment_reports_updated_at
    BEFORE UPDATE ON invoice_payment_reports
    FOR EACH ROW
    EXECUTE FUNCTION update_invoice_payment_reports_updated_at();

COMMENT ON TABLE invoice_payment_reports IS 'Client-reported invoice payments; only ADMIN verification marks an invoice PAID';
COMMENT ON COLUMN invoice_payment_reports.status IS 'PENDING (awaiting admin review), VERIFIED (invoice marked PAID) or REJECTED';
COMMENT ON COLUMN invoice_payment_reports.currency IS 'Copied from the invoice at report time (server-side, not client input)';
