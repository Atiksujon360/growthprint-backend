-- Reset GrowthPrint business data to an empty, working state. LOCAL / DEVELOPMENT DATABASES ONLY.
--
-- Run it through scripts/reset-dev-data.ps1, which refuses non-local hosts, asks you to type the database name and
-- takes a backup first. If you run this file by hand use:  psql -v ON_ERROR_STOP=1 --single-transaction -f reset-dev-data.sql
--
-- DELETES: invoices, payment reports, support requests and estimates, proposals, projects and milestones, outreach
--          drafts, CRM leads, public inquiries, clients and their assignments, every CLIENT account, the three seed
--          accounts (admin@/clienta@/clientb@growthprint.dev), AI usage rows, and every business except the default one.
-- KEEPS:   the schema and flyway_schema_history, the default business, real ADMIN and TEAM_MEMBER accounts,
--          and the single ai_settings row (its provider, model and API key are cleared and AI is switched off).
-- Nothing here changes the schema. It cannot be undone except from a backup.

-- Second line of defence: a remote server never reports a loopback address.
DO $$
BEGIN
    IF inet_server_addr() IS NOT NULL AND NOT (inet_server_addr() <<= '127.0.0.0/8'::inet OR inet_server_addr() = '::1'::inet) THEN
        RAISE EXCEPTION 'Refusing to reset: this database is not on a loopback address (%). Local databases only.', inet_server_addr();
    END IF;
END $$;

\echo 'Row counts BEFORE'
SELECT 'users' AS "table", count(*) FROM users
UNION ALL SELECT 'clients', count(*) FROM clients
UNION ALL SELECT 'crm_leads', count(*) FROM crm_leads
UNION ALL SELECT 'leads (inquiries)', count(*) FROM leads
UNION ALL SELECT 'projects', count(*) FROM projects
UNION ALL SELECT 'invoices', count(*) FROM invoices
UNION ALL SELECT 'support_requests', count(*) FROM support_requests
UNION ALL SELECT 'proposals', count(*) FROM proposals
UNION ALL SELECT 'outreach_drafts', count(*) FROM outreach_drafts
UNION ALL SELECT 'businesses', count(*) FROM businesses
UNION ALL SELECT 'ai_usage', count(*) FROM ai_usage
ORDER BY 1;

-- Children first (foreign keys are RESTRICT for most of these).
DELETE FROM invoice_payment_reports;
DELETE FROM invoice_line_items;
DELETE FROM invoices;
DELETE FROM support_estimates;
DELETE FROM support_requests;
DELETE FROM proposals;
DELETE FROM project_milestones;
DELETE FROM projects;
DELETE FROM outreach_drafts;
DELETE FROM crm_leads;
DELETE FROM leads;
DELETE FROM client_assignments;
DELETE FROM clients;

-- Accounts. Invitations, reset tokens, permissions and business memberships of deleted users cascade.
DELETE FROM users
 WHERE role = 'CLIENT'
    OR normalized_email IN ('admin@growthprint.dev', 'clienta@growthprint.dev', 'clientb@growthprint.dev');

-- Sample businesses made in development (memberships and invitations cascade). The default business stays.
DELETE FROM businesses WHERE id <> '6a1e0b7c-0000-4000-8000-00000000b001';

-- AI: drop the stored (encrypted) key and switch AI off so a test key never survives a reset.
DELETE FROM ai_usage;
UPDATE ai_settings
   SET enabled = FALSE,
       provider = NULL,
       model = NULL,
       base_url = NULL,
       api_key_ciphertext = NULL,
       api_key_iv = NULL,
       api_key_last4 = NULL,
       last_test_status = NULL,
       last_test_at = NULL,
       last_test_message = NULL,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 1;

\echo 'Row counts AFTER'
SELECT 'users (kept: real admins and team)' AS "table", count(*) FROM users
UNION ALL SELECT 'clients', count(*) FROM clients
UNION ALL SELECT 'crm_leads', count(*) FROM crm_leads
UNION ALL SELECT 'leads (inquiries)', count(*) FROM leads
UNION ALL SELECT 'projects', count(*) FROM projects
UNION ALL SELECT 'invoices', count(*) FROM invoices
UNION ALL SELECT 'support_requests', count(*) FROM support_requests
UNION ALL SELECT 'proposals', count(*) FROM proposals
UNION ALL SELECT 'outreach_drafts', count(*) FROM outreach_drafts
UNION ALL SELECT 'businesses (default kept)', count(*) FROM businesses
UNION ALL SELECT 'ai_usage', count(*) FROM ai_usage
ORDER BY 1;

\echo 'Active ADMIN accounts left (if 0, start the app with BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD set):'
SELECT count(*) AS active_admins FROM users WHERE role = 'ADMIN' AND account_status = 'ACTIVE';
