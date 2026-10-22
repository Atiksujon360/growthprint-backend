CREATE TABLE leads (
    id UUID PRIMARY KEY,
    reference_number VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    business_email VARCHAR(254) NOT NULL,
    company VARCHAR(160) NOT NULL,
    website VARCHAR(2048),
    service_category VARCHAR(40) NOT NULL,
    project_type VARCHAR(160) NOT NULL,
    budget_range VARCHAR(120),
    preferred_timeline VARCHAR(120),
    project_description TEXT NOT NULL,
    preferred_contact_method VARCHAR(20) NOT NULL,
    consent BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT leads_consent_true CHECK (consent = TRUE)
);

CREATE INDEX idx_leads_received_at ON leads (received_at DESC);
CREATE INDEX idx_leads_business_email ON leads (business_email);
CREATE INDEX idx_leads_status ON leads (status);
