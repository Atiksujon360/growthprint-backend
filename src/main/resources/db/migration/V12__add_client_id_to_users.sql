-- V12: Add client_id to users table for client-specific data access
-- This migration adds foreign key reference to clients table for CLIENT role users
--
-- SECURITY PURPOSE:
-- - Enables client portal users to access only their own projects
-- - CLIENT role users must have a valid client_id
-- - ADMIN role users have no client_id (can access all projects)
--
-- CLIENT-TO-USER MAPPING:
-- - One-to-many: one client can have multiple users
-- - Foreign key ensures referential integrity
-- - Nullable to allow ADMIN users without client association

ALTER TABLE users
ADD COLUMN IF NOT EXISTS client_id UUID;

-- Add foreign key constraint to clients table
ALTER TABLE users
ADD CONSTRAINT fk_users_client
FOREIGN KEY (client_id)
REFERENCES clients(id)
ON DELETE SET NULL;

-- Add index for client-specific queries
CREATE INDEX idx_users_client_id ON users(client_id);

COMMENT ON COLUMN users.client_id IS 'Foreign key to clients table for CLIENT role users (NULL for ADMIN)';
