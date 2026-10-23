-- V9: Create outreach_drafts table
-- This migration adds outreach draft management.
--
-- OUTREACH MANAGEMENT:
-- - outreach_drafts table stores draft outreach communications
-- - Foreign key to crm_leads table for lead association
-- - Status enum: DRAFT, AWAITING_HUMAN_APPROVAL, APPROVED, SENT, REJECTED
--
-- SAFETY:
-- - No email sending or external provider integration
-- - Record management only
-- - approve operation updates status and approvedAt
-- - send operation updates status and sentAt (no actual email sent)
--
-- RELATIONSHIPS:
-- - outreach_drafts.lead_id → crm_leads.id (ON DELETE CASCADE)

CREATE TABLE IF NOT EXISTS outreach_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id UUID NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    recipient_name VARCHAR(200) NOT NULL,
    recipient_email VARCHAR(200) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    message_body TEXT NOT NULL,
    personalization_evidence TEXT,
    relevant_service VARCHAR(50) NOT NULL,
    risk_flags JSONB DEFAULT '[]'::jsonb NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    approved_by_founder BOOLEAN DEFAULT FALSE,
    approved_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_outreach_drafts_lead FOREIGN KEY (lead_id)
        REFERENCES crm_leads(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_outreach_drafts_lead_id ON outreach_drafts(lead_id);
CREATE INDEX IF NOT EXISTS idx_outreach_drafts_status ON outreach_drafts(status);
CREATE INDEX IF NOT EXISTS idx_outreach_drafts_relevant_service ON outreach_drafts(relevant_service);

-- CHECK constraints
ALTER TABLE outreach_drafts
ADD CONSTRAINT chk_outreach_status
CHECK (status IN ('DRAFT', 'AWAITING_HUMAN_APPROVAL', 'APPROVED', 'SENT', 'REJECTED'));

ALTER TABLE outreach_drafts
ADD CONSTRAINT chk_outreach_relevant_service
CHECK (relevant_service IN ('SOFTWARE_DEV', 'WEBSITE_MAINTENANCE', 'TECHNICAL_SEO', 'BUSINESS_AUTOMATION', 'AI_INTEGRATION'));

-- Trigger function to update updated_at
CREATE OR REPLACE FUNCTION update_outreach_drafts_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger
DROP TRIGGER IF EXISTS trigger_update_outreach_drafts_updated_at ON outreach_drafts;
CREATE TRIGGER trigger_update_outreach_drafts_updated_at
    BEFORE UPDATE ON outreach_drafts
    FOR EACH ROW
    EXECUTE FUNCTION update_outreach_drafts_updated_at();

-- Comments
COMMENT ON TABLE outreach_drafts IS 'Outreach draft communications with lead association';
COMMENT ON COLUMN outreach_drafts.lead_id IS 'Foreign key to crm_leads table';
COMMENT ON COLUMN outreach_drafts.status IS 'Draft status: DRAFT, AWAITING_HUMAN_APPROVAL, APPROVED, SENT, REJECTED';
COMMENT ON COLUMN outreach_drafts.approved_by_founder IS 'Whether the founder/admin approved this draft';
COMMENT ON COLUMN outreach_drafts.approved_at IS 'Timestamp when draft was approved';
COMMENT ON COLUMN outreach_drafts.sent_at IS 'Timestamp when draft was marked as sent (no actual email sent)';
