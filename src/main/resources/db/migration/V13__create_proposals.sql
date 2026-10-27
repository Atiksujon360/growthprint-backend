-- V13: Create proposals table for commercial proposals and scope specifications
-- This table stores formal engineering statements of work, payment milestones, and contract deliverables

CREATE TABLE IF NOT EXISTS proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_number VARCHAR(50) NOT NULL UNIQUE,
    client_id UUID NOT NULL,
    client_name VARCHAR(200) NOT NULL,
    title VARCHAR(300) NOT NULL,
    service_category VARCHAR(50) NOT NULL,
    scope_summary TEXT NOT NULL,
    deliverables JSONB NOT NULL DEFAULT '[]'::jsonb,
    exclusions JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_price DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_milestones JSONB NOT NULL DEFAULT '[]'::jsonb,
    timeline_duration VARCHAR(100) NOT NULL,
    valid_until DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    accepted_at TIMESTAMPTZ,
    accepted_by VARCHAR(200),
    declined_at TIMESTAMPTZ,
    declined_by VARCHAR(200),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_proposals_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE RESTRICT,
    CONSTRAINT proposals_status_valid CHECK (status IN ('DRAFT', 'SENT_FOR_REVIEW', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT proposals_currency_valid CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD', 'AUD')),
    CONSTRAINT proposals_service_category_valid CHECK (service_category IN ('SOFTWARE_DEV', 'WEBSITE_MAINTENANCE', 'TECHNICAL_SEO', 'BUSINESS_AUTOMATION', 'AI_INTEGRATION'))
);

CREATE INDEX IF NOT EXISTS idx_proposals_client_id ON proposals(client_id);
CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals(status);
CREATE INDEX IF NOT EXISTS idx_proposals_proposal_number ON proposals(proposal_number);
CREATE INDEX IF NOT EXISTS idx_proposals_valid_until ON proposals(valid_until);

CREATE OR REPLACE FUNCTION update_proposals_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_proposals_updated_at ON proposals;
CREATE TRIGGER trigger_update_proposals_updated_at
    BEFORE UPDATE ON proposals
    FOR EACH ROW
    EXECUTE FUNCTION update_proposals_updated_at();
