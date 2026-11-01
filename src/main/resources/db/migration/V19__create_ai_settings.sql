-- V19: optional, admin-controlled AI provider configuration and usage records (decision D5, approved 2026-11-01).
-- New tables only; no existing data is changed. AI stays OFF until an admin configures and enables it.

CREATE TABLE IF NOT EXISTS ai_settings (
    id                           SMALLINT     PRIMARY KEY,
    enabled                      BOOLEAN      NOT NULL DEFAULT FALSE,
    provider                     VARCHAR(30),
    model                        VARCHAR(120),
    base_url                     VARCHAR(500),
    timeout_seconds              INTEGER      NOT NULL DEFAULT 60,
    max_output_tokens            INTEGER      NOT NULL DEFAULT 800,
    daily_request_limit_per_user INTEGER      NOT NULL DEFAULT 50,
    -- AES-256-GCM ciphertext of the provider API key; the encryption key is NOT in the database
    -- (AI_CREDENTIALS_ENCRYPTION_KEY environment variable).
    api_key_ciphertext           TEXT,
    api_key_iv                   VARCHAR(32),
    api_key_last4                VARCHAR(4),
    last_test_status             VARCHAR(30),
    last_test_at                 TIMESTAMPTZ,
    last_test_message            VARCHAR(300),
    updated_at                   TIMESTAMPTZ  NOT NULL,
    updated_by_user_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_ai_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_ai_settings_provider CHECK (provider IS NULL OR provider IN
        ('GEMINI', 'OPENAI', 'ANTHROPIC', 'MISTRAL', 'DEEPSEEK', 'OPENAI_COMPATIBLE')),
    CONSTRAINT chk_ai_settings_limits CHECK (
        timeout_seconds BETWEEN 5 AND 120
        AND max_output_tokens BETWEEN 64 AND 8000
        AND daily_request_limit_per_user BETWEEN 1 AND 10000),
    CONSTRAINT chk_ai_settings_key_parts CHECK (
        (api_key_ciphertext IS NULL AND api_key_iv IS NULL AND api_key_last4 IS NULL)
        OR (api_key_ciphertext IS NOT NULL AND api_key_iv IS NOT NULL AND api_key_last4 IS NOT NULL)),
    -- AI can only be on with a complete configuration.
    CONSTRAINT chk_ai_settings_enabled_complete CHECK (
        enabled = FALSE OR (provider IS NOT NULL AND model IS NOT NULL AND api_key_ciphertext IS NOT NULL))
);

INSERT INTO ai_settings (id, enabled, updated_at) VALUES (1, FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- One row per AI call. No prompt or response text is ever stored.
CREATE TABLE IF NOT EXISTS ai_usage (
    id            UUID         PRIMARY KEY,
    user_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    provider      VARCHAR(30)  NOT NULL,
    model         VARCHAR(120) NOT NULL,
    feature       VARCHAR(50)  NOT NULL,
    input_tokens  INTEGER,
    output_tokens INTEGER,
    latency_ms    INTEGER      NOT NULL,
    outcome       VARCHAR(40)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_user_created ON ai_usage (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_usage_created ON ai_usage (created_at);

COMMENT ON TABLE ai_settings IS 'Single-row, admin-managed AI provider configuration (key encrypted at rest)';
COMMENT ON TABLE ai_usage IS 'AI call log for limits and reporting; token counts are provider-reported, never estimated';
