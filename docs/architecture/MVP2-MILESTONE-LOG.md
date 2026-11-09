# MVP2 Milestone Log

Evidence per milestone of the "MVP2 Multi-Tenant Architecture & Implementation Specification v1.0".
Branch: `feature/growthprint-professional-improvements` (backend). Nothing pushed, merged or deployed.

## M0 — Repository stabilization (partial)

| Item | Status |
|---|---|
| Backend CI fixed: `./mvnw` on Linux, JDK 21, PostgreSQL 16 service, `GP_IT_DATABASE_*` set so the 43 `@PostgresIntegrationTest` tests run; reports uploaded | Done in `.github/workflows/ci.yml` — **not yet run on GitHub** (requires a push, CEO approval) |
| Commit the uncommitted work in both repos | **Open — awaiting approval** |
| Schema diff: production vs fresh migration (V1/V11 duplicate DDL risk) | **Open — needs a restored production backup** |

## M1 — Business and membership foundation

**Migration:** `V21__create_businesses_and_memberships.sql` (additive; no existing table altered).
- `businesses` (slug unique and format-checked, status), `business_memberships` (PK business+user, role
  OWNER/ADMIN/MANAGER/MEMBER, status ACTIVE/REMOVED), `business_invitations` (SHA-256 token hash, one pending
  invitation per business+email, OWNER not invitable).
- Default business `GrowthPrint` with fixed id `6a1e0b7c-0000-4000-8000-00000000b001`.
- Existing ADMIN users → OWNER, TEAM_MEMBER users → MEMBER of the default business. CLIENT portal accounts are
  not members (they are customers of the default business).
- Next migration version: **V22**.

**Code:** package `com.growthprint.backend.business`
- `BusinessAccessService.require(businessId, userId, minRole)` — the single gate for tenant access: inactive
  business, missing or removed membership → 404; insufficient role → 403.
- `BusinessService` — list my businesses (switcher), create (creator = OWNER; CLIENT accounts refused), get, rename.
- `BusinessMembershipService` — roles by rank, owner-only owner changes, ≥1 OWNER kept under a business row lock,
  owner-only ownership transfer (old owner → ADMIN), leave/remove, invitations (email-bound, single use, expiring).
- Audit events in `SECURITY_AUDIT`: BUSINESS_CREATED/UPDATED, MEMBERSHIP_ROLE_CHANGED/REMOVED,
  OWNERSHIP_TRANSFERRED, BUSINESS_INVITATION_CREATED/REVOKED/ACCEPTED.

**API (all authenticated; membership checked per business):**
`GET/POST /api/v1/businesses` · `GET/PATCH /api/v1/businesses/{id}` · `GET /{id}/members` ·
`PATCH|DELETE /{id}/members/{userId}` · `POST /{id}/ownership-transfer` · `GET|POST /{id}/invitations` ·
`DELETE /{id}/invitations/{invitationId}` · `POST /api/v1/business-invitations/accept`.
Existing MVP1 endpoints are unchanged.

**Tests**
- `BusinessMembershipServiceTest` (8): non-member/other-tenant 404, rank rules, owner-only owner changes,
  last-owner protection, transfer, leave/remove, invitation email binding and single use.
- `RoleMatrixSecurityTest`: 12 new routes × anonymous/CLIENT/ADMIN/TEAM_MEMBER.
- `BusinessTenancyWorkflowIntegrationTest` (PostgreSQL, 3): default business exists, cross-business reads and
  writes return 404 and change nothing, independent roles per business, ownership transfer and leaving.
- `FlywayMigrationTest`: asserts the three new tables.

**Verification (3 Nov 2026):** `.\mvnw.cmd clean test` with JDK 21 → **858 tests, 0 failures, 0 errors,
43 skipped** (all skipped tests are PostgreSQL-tagged; no isolated database available locally, so the new
tenancy integration tests have **not** run yet).

**Outstanding risks**
- The PostgreSQL tests (including V21 itself) must run in CI or against a restored backup before M2.
- If production has no ACTIVE ADMIN user, the default business would have no OWNER — verify on the restored backup.
- Audit is log-based; a persistent audit history table is planned for M5.
- Frontend business switcher not built yet (milestone M3/M4 UI work).

## Next: M2 — Tenant data migration (requires a restored production backup)

Add `business_id` to clients, leads, crm_leads, projects, project_milestones, support_requests, support_estimates,
proposals, invoices, invoice_line_items, invoice_payment_reports, outreach_drafts (and client users); backfill with
the default business; validate counts; then `NOT NULL`, composite FKs `(business_id, id)` and indexes; then switch
repositories to tenant-scoped queries.

## M1.1 — Security hardening (3 Nov 2026, branch `feature/mvp2-m1-security-hardening`)

V22 (owner eligibility), V23 (lead → client conversion record), gated business creation, lock-then-authorize,
invitation fix, client create + transactional lead conversion, CI skip guard. Local: 881 tests, 0 failures,
46 skipped (PostgreSQL-only + 3 opt-in seed). GitHub CI not run. M2: **BLOCKED** (CI proof + restored backup).
Details: `MVP2-M1-1-SECURITY-HARDENING.md`. Next migration version: **V24**.
