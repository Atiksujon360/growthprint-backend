-- V20: account security, profile preferences and password recovery (approved 2026-11-02).
-- Forward-only and additive: nothing is dropped, renamed or deleted.
-- (V18 is intentionally unused; V19 may already be applied, so the next version is V20.)
--
--   * users.token_version      - included in every JWT ("ver" claim). Incrementing it revokes every token
--                                issued before (password change/reset, "sign out everywhere", account
--                                deactivation, role change). Existing tokens carry no claim and map to 0,
--                                so sessions issued before this migration keep working.
--   * users.password_changed_at - last password change (shown in account settings, audit).
--   * users.phone / job_title / timezone / email_notifications - self-service profile + preferences.
--   * password_reset_tokens    - single-use, time-limited reset tokens. Only the SHA-256 hash of the token
--                                is stored; the raw token exists only in the reset link.

ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(40);
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_notifications BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE users
    ADD CONSTRAINT chk_users_token_version_non_negative CHECK (token_version >= 0);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_password_reset_tokens_expiry CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires ON password_reset_tokens (expires_at);

COMMENT ON COLUMN users.token_version IS 'Incremented to revoke all previously issued JWTs for this user';
COMMENT ON COLUMN users.password_changed_at IS 'When the password was last changed or reset';
COMMENT ON TABLE password_reset_tokens IS 'Single-use password reset tokens; only the SHA-256 hash is stored';
