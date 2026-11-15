# Data reset, first admin and AI key runbook

Who: the project owner. Applies to: removing sample or test data, creating the first admin without a known password, and
recovering the AI configuration. Nothing here is run for you by the application.

## 1. Local or development database
Use the guarded script. It only accepts `localhost`, `127.0.0.1` or `::1`.

```powershell
$env:DATABASE_URL      = 'jdbc:postgresql://localhost:5432/growthprint'
$env:DATABASE_USERNAME = 'postgres'
$env:DATABASE_PASSWORD = '<your local password>'
.\scripts\reset-dev-data.ps1 -Confirm
```
It asks you to type the database name, takes a `pg_dump` backup into `backups\` (git-ignored), then deletes business data in
one transaction. Read `scripts/reset-dev-data.sql` first: it lists exactly what goes and what stays.

- **Removed:** clients, CRM leads, inquiries, projects and milestones, invoices and payment reports, support requests and estimates,
  proposals, outreach drafts, every CLIENT account, the three seed accounts, AI usage rows, and every business except the default one.
- **Kept:** the schema and `flyway_schema_history`, the default business, real ADMIN and TEAM_MEMBER accounts, and the `ai_settings` row
  (provider, model and API key are cleared and AI is switched off).
- **After:** if no ADMIN is left, set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`, start the backend once, sign in, and
  remove the password variable (section 3).

**Check it first, on a throwaway database:** `.\scripts\verify-reset-local.ps1` creates `gp_reset_test`, seeds it, runs the reset
and the bootstrap, checks the results and drops the database. It never touches your real database or `.dev-credentials`.

## 2. Hosted database (Render): do this by hand, not with the script
The script refuses any non-local host on purpose. For the hosted database:
1. **Back up first.** Take a manual backup in the Render dashboard (or `pg_dump`), and confirm you can restore it into an
   *empty, separate* database. Do not continue without a restore you have actually tried.
2. Run `check-users.sql` (read-only) to see whether the seed accounts (`admin@`, `clienta@`, `clientb@growthprint.dev`) exist.
   If they do, their passwords were committed to source in older versions: treat them as compromised.
3. If you need a data reset there, run `scripts/reset-dev-data.sql` yourself through a SQL client **after** step 1, reading every
   statement first. Its in-database guard refuses non-loopback servers, so you must remove that block deliberately: that is the
   point where you confirm the target.
4. Rotate: `JWT_SECRET` (signs everyone out), any seed account passwords, and the AI encryption key if it was ever shared.

## 3. First admin (no seeded password)
On a database with no ACTIVE admin, start the backend with both variables set:
```
BOOTSTRAP_ADMIN_EMAIL=you@yourcompany.com
BOOTSTRAP_ADMIN_PASSWORD=<12+ characters, a letter and a number>
```
The admin is created and made OWNER of the default business. **Remove `BOOTSTRAP_ADMIN_PASSWORD` immediately after the first
sign-in.** If an active admin already exists nothing is created; an existing account is never changed. A weak password stops the
application with a message that names the problem but never prints the password.

## 4. AI provider setup and recovery
Studio → AI providers has three steps: set up (provider, model, key), test the connection, turn on. Turning on is only possible
after a passing test, and changing the provider, model or endpoint clears that test.
- **Needs** `AI_CREDENTIALS_ENCRYPTION_KEY` (`openssl rand -base64 32`). In the `prod` profile the application will not start if it
  is malformed, or if AI is on but the key is missing or can't read the saved API key.
- **Saved key can't be read** (the encryption key changed): the screen says so; paste the provider key again.
- **Changing provider or custom endpoint** removes the saved key (the screen asks first). A key only ever goes to the service it was saved for.
- **Custom endpoints** must be public `https://` addresses. Private, local and cloud-metadata addresses are refused. Local model servers
  work only with `AI_ALLOW_LOCAL_ENDPOINTS=true` (the `dev` profile sets it). Never set it in production.
- **Known limit:** the endpoint is checked when saved and again before each call, but a host that changes its DNS answer between the
  check and the connection can't be fully excluded without pinning the address. Only administrators can set the endpoint.

## Rollback
Code changes: revert the files. A data reset cannot be undone except from the backup taken in step 1 (or `backups\*.dump` locally):
`pg_restore -d <empty database> backups\<file>.dump`.
