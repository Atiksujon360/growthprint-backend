-- V5: Create users table for authentication and authorization
-- This migration adds secure user persistence for JWT-based authentication.
--
-- SECURITY NOTES:
-- - Email addresses are normalized to lowercase for uniqueness
-- - Passwords are stored as BCrypt hashes (never plaintext)
-- - Roles are limited to ADMIN and CLIENT (GUEST is not an authenticated role)
-- - Account status controls whether a user can authenticate
-- - Initial administrator must be created via secure deployment process (not in migration)
--
-- INITIAL ADMIN PROVISIONING:
-- This migration does NOT create a default administrator with hardcoded credentials.
-- The first administrator must be created manually after deployment using:
-- 1. A secure deployment script that hashes passwords via BCrypt
-- 2. Direct database insertion with a properly hashed password
-- 3. A future secure admin-creation endpoint (requires existing authenticated admin)
--
-- Example BCrypt hash for testing (DO NOT use in production):
-- Hash for "ChangeMe123!" is generated via BCryptPasswordEncoder.encode()

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique constraint on normalized email (case-insensitive uniqueness)
CREATE UNIQUE INDEX idx_users_normalized_email ON users(normalized_email);

-- Role constraint: only ADMIN and CLIENT are valid authenticated roles
ALTER TABLE users
ADD CONSTRAINT chk_users_role
CHECK (role IN ('ADMIN', 'CLIENT'));

-- Account status constraint
ALTER TABLE users
ADD CONSTRAINT chk_users_account_status
CHECK (account_status IN ('ACTIVE', 'DISABLED'));

-- Index for login queries
CREATE INDEX idx_users_email ON users(email);

-- Index for role-based queries
CREATE INDEX idx_users_role ON users(role);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_users_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_users_updated_at();

COMMENT ON TABLE users IS 'User accounts for JWT-based authentication';
COMMENT ON COLUMN users.email IS 'User email address (original case)';
COMMENT ON COLUMN users.normalized_email IS 'Email normalized to lowercase for uniqueness';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash of user password (never plaintext)';
COMMENT ON COLUMN users.role IS 'User role: ADMIN or CLIENT (GUEST is not an authenticated role)';
COMMENT ON COLUMN users.account_status IS 'Account status: ACTIVE or DISABLED';
