-- V7: Add deliverables column to projects table
-- This migration adds a JSONB column to store project deliverables.
--
-- DELIVERABLES PERSISTENCE:
-- - deliverables stored as JSONB array of strings
-- - Allows flexible storage and querying of deliverables
-- - Maintains compatibility with frontend contract (deliverables: string[])
--
-- JSONB FORMAT:
-- - Array of strings: ["Deliverable 1", "Deliverable 2", ...]
-- - Can be queried with PostgreSQL JSONB operators
-- - Default to empty array '[]' for consistency

ALTER TABLE projects
ADD COLUMN deliverables JSONB DEFAULT '[]'::jsonb NOT NULL;

-- Add index for JSONB queries (optional, for future filtering)
-- CREATE INDEX idx_projects_deliverables ON projects USING GIN (deliverables);

COMMENT ON COLUMN projects.deliverables IS 'Project deliverables as JSONB array of strings';
