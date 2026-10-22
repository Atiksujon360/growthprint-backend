-- V6: Create projects and project_milestones tables
-- This migration adds project and milestone persistence for the GrowthPrint platform.
--
-- RELATIONSHIP MODEL:
-- - projects table references clients.id (foreign key)
-- - project_milestones table references projects.id (foreign key)
-- - Milestones are stored in a separate table (relational model)
-- - Milestones are assembled into nested array in ProjectResponse DTO
--
-- ENUMS:
-- - ProjectStatus: DISCOVERY, PLANNED, IN_PROGRESS, WAITING_FOR_CLIENT, IN_REVIEW, COMPLETED, ON_HOLD
-- - MilestoneStatus: PENDING, IN_PROGRESS, COMPLETED
-- - ServiceCategory: SOFTWARE_DEV, WEBSITE_MAINTENANCE, TECHNICAL_SEO, BUSINESS_AUTOMATION, AI_INTEGRATION
--
-- SECURITY:
-- - All project endpoints require ADMIN role (until client portal authorization implemented)
-- - No CLIENT access to all projects
-- - Client-specific project access requires future client-to-user mapping
--
-- DELETION BEHAVIOR:
-- - Cascading delete: Deleting a project deletes its milestones (ON DELETE CASCADE)
-- - Restrict delete: Cannot delete a client that has projects (foreign key constraint)
--
-- INDEXES:
-- - Index on client_id for client-specific project queries
-- - Index on status for filtering by project status
-- - Index on project_id for milestone queries
-- - Index on milestone status for filtering

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

    CONSTRAINT fk_projects_client FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE RESTRICT
);

-- Unique constraint on project reference
CREATE UNIQUE INDEX idx_projects_reference ON projects(reference);

-- Index for client-specific project queries
CREATE INDEX idx_projects_client_id ON projects(client_id);

-- Index for status filtering
CREATE INDEX idx_projects_status ON projects(status);

-- Index for service category filtering
CREATE INDEX idx_projects_service_category ON projects(service_category);

-- Index for sorting by target completion date
CREATE INDEX idx_projects_target_completion_date ON projects(target_completion_date);

-- CHECK constraint for project status
ALTER TABLE projects
ADD CONSTRAINT chk_projects_status
CHECK (status IN ('DISCOVERY', 'PLANNED', 'IN_PROGRESS', 'WAITING_FOR_CLIENT', 'IN_REVIEW', 'COMPLETED', 'ON_HOLD'));

-- CHECK constraint for service category
ALTER TABLE projects
ADD CONSTRAINT chk_projects_service_category
CHECK (service_category IN ('SOFTWARE_DEV', 'WEBSITE_MAINTENANCE', 'TECHNICAL_SEO', 'BUSINESS_AUTOMATION', 'AI_INTEGRATION'));

-- CHECK constraint for progress percentage (0-100)
ALTER TABLE projects
ADD CONSTRAINT chk_projects_progress_percent
CHECK (progress_percent >= 0 AND progress_percent <= 100);

-- CHECK constraint for budget (spent cannot exceed total)
ALTER TABLE projects
ADD CONSTRAINT chk_projects_budget
CHECK (budget_spent >= 0 AND budget_spent <= budget_total);

CREATE TABLE IF NOT EXISTS project_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_milestones_project FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE
);

-- Index for project-specific milestone queries
CREATE INDEX idx_milestones_project_id ON project_milestones(project_id);

-- Index for milestone status filtering
CREATE INDEX idx_milestones_status ON project_milestones(status);

-- Index for due date sorting
CREATE INDEX idx_milestones_due_date ON project_milestones(due_date);

-- CHECK constraint for milestone status
ALTER TABLE project_milestones
ADD CONSTRAINT chk_milestones_status
CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED'));

-- Trigger to update projects.updated_at
CREATE OR REPLACE FUNCTION update_projects_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_projects_updated_at
BEFORE UPDATE ON projects
FOR EACH ROW
EXECUTE FUNCTION update_projects_updated_at();

-- Trigger to update project_milestones.updated_at
CREATE OR REPLACE FUNCTION update_milestones_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_milestones_updated_at
BEFORE UPDATE ON project_milestones
FOR EACH ROW
EXECUTE FUNCTION update_milestones_updated_at();

COMMENT ON TABLE projects IS 'Project records for client work';
COMMENT ON COLUMN projects.reference IS 'Unique project reference identifier (e.g., GP-PRJ-2026-001)';
COMMENT ON COLUMN projects.client_id IS 'Foreign key to clients table';
COMMENT ON COLUMN projects.service_category IS 'Type of service provided';
COMMENT ON COLUMN projects.progress_percent IS 'Overall completion percentage (0-100)';
COMMENT ON COLUMN projects.budget_total IS 'Total budget for the project';
COMMENT ON COLUMN projects.budget_spent IS 'Amount spent to date';
COMMENT ON COLUMN projects.assigned_lead IS 'Name of the team member leading the project';

COMMENT ON TABLE project_milestones IS 'Milestones for project tracking';
COMMENT ON COLUMN project_milestones.project_id IS 'Foreign key to projects table';
COMMENT ON COLUMN project_milestones.due_date IS 'Target completion date for the milestone';
COMMENT ON COLUMN project_milestones.status IS 'Current milestone status';
