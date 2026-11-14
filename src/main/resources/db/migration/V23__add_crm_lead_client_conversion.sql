-- V23 (M1.1): record which client a CRM lead was converted into (lead -> client conversion).
-- Additive and nullable: existing rows are unchanged.
--   * converted_client_id: the client created from this lead (RESTRICT: a client that a lead points to cannot be
--     deleted silently; no client-delete endpoint exists today).
--   * converted_at: when the conversion happened.
--   * A lead converts at most once (application check under a row lock) and a client comes from at most one lead
--     (unique partial index) — the database rejects duplicate conversions even under concurrency.

ALTER TABLE crm_leads ADD COLUMN IF NOT EXISTS converted_client_id UUID;
ALTER TABLE crm_leads ADD COLUMN IF NOT EXISTS converted_at TIMESTAMPTZ;

ALTER TABLE crm_leads
    ADD CONSTRAINT fk_crm_leads_converted_client FOREIGN KEY (converted_client_id) REFERENCES clients (id)
        ON DELETE RESTRICT;

ALTER TABLE crm_leads
    ADD CONSTRAINT chk_crm_leads_conversion_complete CHECK (
        (converted_client_id IS NULL AND converted_at IS NULL)
        OR (converted_client_id IS NOT NULL AND converted_at IS NOT NULL));

CREATE UNIQUE INDEX IF NOT EXISTS uk_crm_leads_converted_client
    ON crm_leads (converted_client_id) WHERE converted_client_id IS NOT NULL;

COMMENT ON COLUMN crm_leads.converted_client_id IS 'Client created from this lead (set once by lead -> client conversion)';
