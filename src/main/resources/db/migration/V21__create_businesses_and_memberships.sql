-- V21: MVP2 tenancy foundation (milestone M1, "expand" step only).
-- Additive: creates businesses, memberships and invitations and the default GrowthPrint business.
-- No existing table is altered, no row is changed or deleted. Business ownership columns on existing
-- tables (clients, leads, projects, ...) are added in a later migration (milestone M2) after a rehearsal
-- on a restored production backup.
--
-- Identity stays global (users); access to a business comes only from business_memberships.
-- Roles: OWNER > ADMIN > MANAGER > MEMBER. A business must always keep at least one active OWNER
-- (enforced in BusinessMembershipService, which locks the business row while changing owners).

CREATE TABLE IF NOT EXISTS businesses (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(160) NOT NULL,
    slug               VARCHAR(80)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_businesses_slug UNIQUE (slug),
    CONSTRAINT chk_businesses_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT chk_businesses_slug_format CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,78}[a-z0-9])?$')
);

CREATE TABLE IF NOT EXISTS business_memberships (
    business_id        UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role               VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (business_id, user_id),
    CONSTRAINT chk_business_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'MEMBER')),
    CONSTRAINT chk_business_memberships_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);
CREATE INDEX IF NOT EXISTS idx_business_memberships_user ON business_memberships (user_id, status);

CREATE TABLE IF NOT EXISTS business_invitations (
    id                 UUID PRIMARY KEY,
    business_id        UUID         NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    normalized_email   VARCHAR(254) NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    token_hash         CHAR(64)     NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    invited_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    accepted_at        TIMESTAMPTZ,
    accepted_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    revoked_at         TIMESTAMPTZ,
    CONSTRAINT uk_business_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_business_invitations_role CHECK (role IN ('ADMIN', 'MANAGER', 'MEMBER')),
    CONSTRAINT chk_business_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);
-- One open invitation per (business, email).
CREATE UNIQUE INDEX IF NOT EXISTS uk_business_invitations_one_pending
    ON business_invitations (business_id, normalized_email) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_business_invitations_business ON business_invitations (business_id, status);

-- ---------------------------------------------------------------------------------------------
-- Default business for all MVP1 data. Fixed id so later migrations can backfill business_id with it.
-- ---------------------------------------------------------------------------------------------
INSERT INTO businesses (id, name, slug, status, created_at, updated_at)
VALUES ('6a1e0b7c-0000-4000-8000-00000000b001', 'GrowthPrint', 'growthprint', 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

-- Existing internal staff join the default business: active ADMIN users become OWNERs (the spec's
-- "existing authorized admin user"), TEAM_MEMBERs become MEMBERs. Client portal accounts (role CLIENT)
-- are external customers of the default business, not members, so they are not added.
INSERT INTO business_memberships (business_id, user_id, role, status, created_at, updated_at)
SELECT '6a1e0b7c-0000-4000-8000-00000000b001', u.id,
       CASE WHEN u.role = 'ADMIN' THEN 'OWNER' ELSE 'MEMBER' END,
       'ACTIVE', now(), now()
FROM users u
WHERE u.role IN ('ADMIN', 'TEAM_MEMBER')
  AND u.account_status IN ('ACTIVE', 'INVITED', 'SUSPENDED')
ON CONFLICT (business_id, user_id) DO NOTHING;

COMMENT ON TABLE businesses IS 'Tenants. Every business-owned record will reference one (M2).';
COMMENT ON TABLE business_memberships IS 'Global user -> business access with a per-business role';
COMMENT ON TABLE business_invitations IS 'One-time invitations to join a business; only the SHA-256 token hash is stored';
