-- V11: Add missing tables that were not created in earlier migrations
-- These tables are required by V9 and V10 but may have been deleted or not created

-- Create clients table if missing
CREATE TABLE IF NOT EXISTS clients (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    legal_name VARCHAR(160) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    phone VARCHAR(50),
    website VARCHAR(2048) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retainer_tier VARCHAR(50),
    monthly_retainer_hours INTEGER,
    consumed_hours_current_month INTEGER,
    contract_start_date DATE NOT NULL,
    account_manager VARCHAR(120) NOT NULL,
    billing_currency VARCHAR(3) NOT NULL,
    notes TEXT,
    CONSTRAINT clients_status_valid CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED')),
    CONSTRAINT clients_retainer_tier_valid CHECK (retainer_tier IS NULL OR retainer_tier IN ('ESSENTIAL_MAINTENANCE', 'PRO_GROWTH', 'NONE')),
    CONSTRAINT clients_billing_currency_valid CHECK (billing_currency IN ('USD', 'EUR', 'GBP'))
);

CREATE INDEX IF NOT EXISTS idx_clients_status ON clients (status);
CREATE INDEX IF NOT EXISTS idx_clients_account_manager ON clients (account_manager);
CREATE INDEX IF NOT EXISTS idx_clients_contract_start_date ON clients (contract_start_date);

-- Create crm_leads table if missing
CREATE TABLE IF NOT EXISTS crm_leads (
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

CREATE INDEX IF NOT EXISTS idx_crm_leads_pipeline_stage ON crm_leads (pipeline_stage);
CREATE INDEX IF NOT EXISTS idx_crm_leads_qualification_score ON crm_leads (qualification_score);
CREATE INDEX IF NOT EXISTS idx_crm_leads_source ON crm_leads (source);
CREATE INDEX IF NOT EXISTS idx_crm_leads_inquiry_id ON crm_leads (inquiry_id);

-- Create projects table if missing
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    client_id UUID NOT NULL,
    service_category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    target_completion_date DATE NOT NULL,
    budget_total DECIMAL(12,2) NOT NULL,
    budget_spent DECIMAL(12,2) NOT NULL DEFAULT 0,
    assigned_lead VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_projects_reference ON projects(reference);
CREATE INDEX IF NOT EXISTS idx_projects_client_id ON projects(client_id);
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);
CREATE INDEX IF NOT EXISTS idx_projects_service_category ON projects(service_category);
CREATE INDEX IF NOT EXISTS idx_projects_target_completion_date ON projects(target_completion_date);

CREATE OR REPLACE FUNCTION update_projects_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_projects_updated_at ON projects;
CREATE TRIGGER trigger_update_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW
    EXECUTE FUNCTION update_projects_updated_at();

-- Create project_milestones table if missing
CREATE TABLE IF NOT EXISTS project_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_milestones_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_milestones_project_id ON project_milestones(project_id);
CREATE INDEX IF NOT EXISTS idx_milestones_status ON project_milestones(status);
CREATE INDEX IF NOT EXISTS idx_milestones_due_date ON project_milestones(due_date);

CREATE OR REPLACE FUNCTION update_milestones_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_milestones_updated_at ON project_milestones;
CREATE TRIGGER trigger_update_milestones_updated_at
    BEFORE UPDATE ON project_milestones
    FOR EACH ROW
    EXECUTE FUNCTION update_milestones_updated_at();
