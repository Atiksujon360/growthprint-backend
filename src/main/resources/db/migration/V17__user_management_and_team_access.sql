-- V17: user management, invitations and team access (decision D1, approved 2026-11-01).
-- Forward-only and non-destructive: no table, column or row is dropped or deleted.
--   * users gain a display name, a client sub-role, last-login time and creator.
--   * roles: ADMIN | TEAM_MEMBER | CLIENT.  account states: INVITED | ACTIVE | SUSPENDED | DISABLED.
--   * invited users have no password yet, so password_hash becomes nullable.
--   * one-time invitation tokens are stored only as SHA-256 hashes.
--   * clients gain an owning team member and an explicit assignment list (team-member access).
--   * team members get per-module permissions.

-- ---------------------------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(160);
ALTER TABLE users ADD COLUMN IF NOT EXISTS client_role VARCHAR(40);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by_user_id UUID;

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'TEAM_MEMBER', 'CLIENT'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_account_status;
ALTER TABLE users
    ADD CONSTRAINT chk_users_account_status CHECK (account_status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'DISABLED'));

ALTER TABLE users
    ADD CONSTRAINT chk_users_client_role CHECK (
        client_role IS NULL
        OR client_role IN ('CLIENT_OWNER', 'CLIENT_PROJECT_MANAGER', 'CLIENT_FINANCE', 'CLIENT_MEMBER'));

-- Existing client logins had full access to their company's portal; keep that by making them owners.
UPDATE users SET client_role = 'CLIENT_OWNER' WHERE role = 'CLIENT' AND client_role IS NULL;

-- Client logins must belong to a client and have a client role; staff logins must not.
-- NOT VALID: enforced for every new or updated row without rejecting historic rows that
-- predate this rule (for example a client login whose client was deleted, leaving client_id NULL).
ALTER TABLE users
    ADD CONSTRAINT chk_users_client_membership CHECK (
        (role = 'CLIENT' AND client_id IS NOT NULL AND client_role IS NOT NULL)
        OR (role <> 'CLIENT' AND client_id IS NULL AND client_role IS NULL)) NOT VALID;

-- Only an active account may have no password if it was invited and has not accepted yet.
ALTER TABLE users
    ADD CONSTRAINT chk_users_password_set CHECK (
        account_status = 'INVITED' OR password_hash IS NOT NULL) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_users_account_status ON users (account_status);

-- ---------------------------------------------------------------------------------------------
-- invitations
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_invitations (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash         CHAR(64)     NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    created_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    accepted_at        TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    CONSTRAINT uk_user_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_user_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);

-- At most one open invitation per user.
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_invitations_one_pending
    ON user_invitations (user_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_user_invitations_user ON user_invitations (user_id);

-- ---------------------------------------------------------------------------------------------
-- team access to clients
-- ---------------------------------------------------------------------------------------------
ALTER TABLE clients ADD COLUMN IF NOT EXISTS owner_user_id UUID;
ALTER TABLE clients
    ADD CONSTRAINT fk_clients_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_clients_owner_user ON clients (owner_user_id);

CREATE TABLE IF NOT EXISTS client_assignments (
    client_id          UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assigned_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (client_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_client_assignments_user ON client_assignments (user_id);

CREATE TABLE IF NOT EXISTS team_member_permissions (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    module  VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, module),
    CONSTRAINT chk_team_member_permissions_module CHECK (
        module IN ('CRM', 'CLIENTS', 'PROJECTS', 'SUPPORT', 'PROPOSALS', 'OUTREACH'))
);

COMMENT ON COLUMN users.client_role IS 'Client portal role (CLIENT users only): owner, project manager, finance or member';
COMMENT ON COLUMN clients.owner_user_id IS 'Team member who owns the client relationship (NULL = company-owned, admins only)';
COMMENT ON TABLE user_invitations IS 'One-time account setup invitations; only the SHA-256 hash of the token is stored';
