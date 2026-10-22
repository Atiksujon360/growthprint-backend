-- CRM Leads Table: Qualified sales prospects with qualification scoring and pipeline tracking
-- Separate from raw inquiries (leads table) to preserve original submission data
CREATE TABLE crm_leads (
    id UUID PRIMARY KEY,
    company_name VARCHAR(160) NOT NULL,
    website VARCHAR(2048) NOT NULL,
    industry VARCHAR(120) NOT NULL,
    location VARCHAR(120) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    contact_title VARCHAR(120),
    source VARCHAR(30) NOT NULL,
    source_url VARCHAR(2048),
    discovered_date TIMESTAMPTZ NOT NULL,
    target_service VARCHAR(40) NOT NULL,
    identified_problem TEXT NOT NULL,
    research_summary TEXT NOT NULL,
    qualification_score INTEGER NOT NULL,
    business_fit_score INTEGER,
    evidence_of_need_score INTEGER,
    contactability_score INTEGER,
    service_relevance_score INTEGER,
    pipeline_stage VARCHAR(30) NOT NULL,
    last_contact_date TIMESTAMPTZ,
    next_action VARCHAR(500) NOT NULL,
    do_not_contact BOOLEAN NOT NULL,
    notes TEXT,
    inquiry_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_crm_lead_company_email UNIQUE (company_name, contact_email),
    CONSTRAINT crm_leads_source_valid CHECK (source IN ('INBOUND_INQUIRY', 'PUBLIC_DIRECTORY', 'TECH_AUDIT_DISCOVERY', 'REFERRAL')),
    CONSTRAINT crm_leads_target_service_valid CHECK (target_service IN ('SOFTWARE_DEV', 'WEBSITE_MAINTENANCE', 'TECHNICAL_SEO', 'BUSINESS_AUTOMATION', 'AI_INTEGRATION')),
    CONSTRAINT crm_leads_pipeline_stage_valid CHECK (pipeline_stage IN ('DISCOVERED', 'RESEARCHING', 'QUALIFIED', 'NEEDS_REVIEW', 'READY_FOR_OUTREACH', 'OUTREACH_APPROVED', 'CONTACTED', 'REPLIED', 'MEETING_SCHEDULED', 'PROPOSAL_SENT', 'WON', 'LOST', 'DO_NOT_CONTACT')),
    CONSTRAINT crm_leads_qualification_score_valid CHECK (qualification_score >= 0 AND qualification_score <= 100),
    CONSTRAINT crm_leads_component_scores_valid CHECK (
        (business_fit_score IS NULL OR (business_fit_score >= 0 AND business_fit_score <= 25)) AND
        (evidence_of_need_score IS NULL OR (evidence_of_need_score >= 0 AND evidence_of_need_score <= 25)) AND
        (contactability_score IS NULL OR (contactability_score >= 0 AND contactability_score <= 25)) AND
        (service_relevance_score IS NULL OR (service_relevance_score >= 0 AND service_relevance_score <= 25))
    )
);

CREATE INDEX idx_crm_leads_pipeline_stage ON crm_leads (pipeline_stage);
CREATE INDEX idx_crm_leads_qualification_score ON crm_leads (qualification_score);
CREATE INDEX idx_crm_leads_source ON crm_leads (source);
CREATE INDEX idx_crm_leads_inquiry_id ON crm_leads (inquiry_id);

-- Foreign key to original inquiry (optional, allows manual CRM leads without inquiry)
ALTER TABLE crm_leads ADD CONSTRAINT fk_crm_leads_inquiry 
    FOREIGN KEY (inquiry_id) REFERENCES leads(id) ON DELETE SET NULL;
