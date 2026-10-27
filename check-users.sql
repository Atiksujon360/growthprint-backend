-- Check existing users in the database
SELECT id, email, normalized_email, role, account_status, client_id, created_at
FROM users
ORDER BY created_at;
