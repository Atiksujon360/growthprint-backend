-- V10: Create support_requests and support_estimates tables
-- This migration adds support request and estimate persistence for the GrowthPrint platform.
--
-- RELATIONSHIP MODEL:
-- - support_requests table references clients.id (foreign key)
-- - support_requests table references projects.id (foreign key, nullable)
-- - support_estimates table references support_requests.id (foreign key)
--
-- ENUMS:
-- - SupportCategory: INCLUDED_SUPPORT, PAID_AD_HOC_SUPPORT, NEW_PROJECT, WARRANTY_OR_DEFECT_REVIEW
-- - SupportRequestStatus: NEW, NEEDS_CLASSIFICATION, ESTIMATE_PENDING, AWAITING_CLIENT_APPROVAL, READY_TO_START, IN_PROGRESS, WAITING_FOR_CLIENT, COMPLETED
-- - BusinessImpact: LOW, MEDIUM, CRITICAL_BLOCKER
-- - Urgency: NORMAL, URGENT
-- - EstimateStatus: PENDING_APPROVAL, ACCEPTED, DECLINED
--
-- SECURITY:
-- - All support request endpoints require ADMIN role (until client portal authorization implemented)
-- - No CLIENT access to all support requests
-- - Client-specific support request access requires future client-to-user mapping
--
-- DELETION BEHAVIOR:
-- - Cascading delete: Deleting a support request deletes its estimates (ON DELETE CASCADE)
-- - Restrict delete: Cannot delete a client that has support requests (foreign key constraint)
-- - Restrict delete: Cannot delete a project that has support requests (foreign key constraint)
--
-- INDEXES:
-- - Index on client_id for client-specific support request queries
-- - Index on project_id for project-specific support request queries
-- - Index on status for filtering by support request status
-- - Index on category for filtering by support category
-- - Index on urgency for urgent request filtering

CREATE TABLE IF NOT EXISTS support_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(50) NOT NULL,
    client_id UUID NOT NULL,
    project_id UUID,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    business_impact VARCHAR(50) NOT NULL,
    urgency VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    admin_notes TEXT,
    resolution_summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_support_requests_client FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_support_requests_project FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE RESTRICT
);

-- Unique constraint on support request reference
CREATE UNIQUE INDEX idx_support_requests_reference ON support_requests(reference);

-- Index for client-specific support request queries
CREATE INDEX idx_support_requests_client_id ON support_requests(client_id);

-- Index for project-specific support request queries
CREATE INDEX idx_support_requests_project_id ON support_requests(project_id);

-- Index for status filtering
CREATE INDEX idx_support_requests_status ON support_requests(status);

-- Index for category filtering
CREATE INDEX idx_support_requests_category ON support_requests(category);

-- Index for urgency filtering
CREATE INDEX idx_support_requests_urgency ON support_requests(urgency);

-- CHECK constraint for support category
ALTER TABLE support_requests
ADD CONSTRAINT chk_support_requests_category
CHECK (category IN ('INCLUDED_SUPPORT', 'PAID_AD_HOC_SUPPORT', 'NEW_PROJECT', 'WARRANTY_OR_DEFECT_REVIEW'));

-- CHECK constraint for support request status
ALTER TABLE support_requests
ADD CONSTRAINT chk_support_requests_status
CHECK (status IN ('NEW', 'NEEDS_CLASSIFICATION', 'ESTIMATE_PENDING', 'AWAITING_CLIENT_APPROVAL', 'READY_TO_START', 'IN_PROGRESS', 'WAITING_FOR_CLIENT', 'COMPLETED'));

-- CHECK constraint for business impact
ALTER TABLE support_requests
ADD CONSTRAINT chk_support_requests_business_impact
CHECK (business_impact IN ('LOW', 'MEDIUM', 'CRITICAL_BLOCKER'));

-- CHECK constraint for urgency
ALTER TABLE support_requests
ADD CONSTRAINT chk_support_requests_urgency
CHECK (urgency IN ('NORMAL', 'URGENT'));

CREATE TABLE IF NOT EXISTS support_estimates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    estimated_hours DECIMAL(10,2) NOT NULL,
    hourly_rate DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    delivery_timeline_days INTEGER NOT NULL,
    explanation TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP WITH TIME ZONE,
    accepted_by VARCHAR(200),

    CONSTRAINT fk_support_estimates_request FOREIGN KEY (request_id)
        REFERENCES support_requests(id)
        ON DELETE CASCADE
);

-- Index for request-specific estimate queries
CREATE INDEX idx_support_estimates_request_id ON support_estimates(request_id);

-- Index for estimate status filtering
CREATE INDEX idx_support_estimates_status ON support_estimates(status);

-- CHECK constraint for estimate status
ALTER TABLE support_estimates
ADD CONSTRAINT chk_support_estimates_status
CHECK (status IN ('PENDING_APPROVAL', 'ACCEPTED', 'DECLINED'));

-- CHECK constraint for currency
ALTER TABLE support_estimates
ADD CONSTRAINT chk_support_estimates_currency
CHECK (currency IN ('USD', 'EUR', 'GBP'));

-- CHECK constraint for estimated hours (must be positive)
ALTER TABLE support_estimates
ADD CONSTRAINT chk_support_estimates_estimated_hours
CHECK (estimated_hours > 0);

-- CHECK constraint for hourly rate (must be non-negative)
ALTER TABLE support_estimates
ADD CONSTRAINT chk_support_estimates_hourly_rate
CHECK (hourly_rate >= 0);

-- CHECK constraint for delivery timeline days (must be positive)
ALTER TABLE support_estimates
ADD CONSTRAINT chk_support_estimates_delivery_timeline_days
CHECK (delivery_timeline_days > 0);

-- Trigger to update support_requests.updated_at
CREATE OR REPLACE FUNCTION update_support_requests_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_support_requests_updated_at
BEFORE UPDATE ON support_requests
FOR EACH ROW
EXECUTE FUNCTION update_support_requests_updated_at();

COMMENT ON TABLE support_requests IS 'Support request records for client service requests';
COMMENT ON COLUMN support_requests.reference IS 'Unique support request reference identifier (e.g., GP-REQ-2026-001)';
COMMENT ON COLUMN support_requests.client_id IS 'Foreign key to clients table';
COMMENT ON COLUMN support_requests.project_id IS 'Foreign key to projects table (optional)';
COMMENT ON COLUMN support_requests.category IS 'Type of support requested';
COMMENT ON COLUMN support_requests.business_impact IS 'Business impact level of the request';
COMMENT ON COLUMN support_requests.urgency IS 'Urgency level of the request';
COMMENT ON COLUMN support_requests.admin_notes IS 'Internal admin notes for classification';
COMMENT ON COLUMN support_requests.resolution_summary IS 'Summary of resolution when completed';

COMMENT ON TABLE support_estimates IS 'Estimates for support requests';
COMMENT ON COLUMN support_estimates.request_id IS 'Foreign key to support_requests table';
COMMENT ON COLUMN support_estimates.estimated_hours IS 'Estimated hours to complete the work';
COMMENT ON COLUMN support_estimates.hourly_rate IS 'Hourly rate for the work';
COMMENT ON COLUMN support_estimates.total_amount IS 'Total estimate amount (subtotal - discount)';
COMMENT ON COLUMN support_estimates.delivery_timeline_days IS 'Expected delivery timeline in days';
COMMENT ON COLUMN support_estimates.accepted_at IS 'Timestamp when estimate was accepted';
COMMENT ON COLUMN support_estimates.accepted_by IS 'User who accepted the estimate';
