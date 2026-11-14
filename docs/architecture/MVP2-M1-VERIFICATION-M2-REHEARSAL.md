# MVP2 — M1 Verification and M2 Migration Rehearsal

**Date:** 3 November 2026 · **Branch:** `feature/growthprint-professional-improvements` (backend)
**Outcome:** M0/M1 verified as far as possible locally. **M2 rehearsal NOT performed — stop condition hit:
no isolated restored database is available** (details in §3). No database was contacted, no code was changed,
nothing was committed, pushed or deployed.

---

## 1. GitHub CI evidence

| Check | Result |
|---|---|
| Workflow file | `.github/workflows/ci.yml` (rewritten in M0, uncommitted) |
| Linux build command | `chmod +x mvnw && ./mvnw -B clean verify` ✔ |
| JDK | `actions/setup-java@v4`, Temurin 21, Maven cache ✔ |
| PostgreSQL service | `postgres:16-alpine`, DB `growthprint_ci`, port 5432, `pg_isready` health check (5 s interval, 10 retries) ✔ |
| Credentials | Throwaway CI-only values in workflow `env` (`GP_IT_DATABASE_URL/USERNAME/PASSWORD`); not production secrets ✔ |
| Flyway | Runs automatically per test context into an isolated per-run schema (`PostgresTestDatabase`, refuses `render.com` hosts) ✔ |
| Integration "profile" | None needed: `@PostgresIntegrationTest` enables itself when `GP_IT_DATABASE_URL` is set ✔ |
| Triggers | `push` to `main` and `pull_request` to `main` only → **pushes to the feature branch do not run CI** ⚠ |
| **Actual GitHub run** | **None verified.** The workflow change is uncommitted; `gh` CLI is not installed here; the remote feature branch (`67169df`) predates the change. **I cannot claim CI passes.** |

**Predicted CI result once it runs: FAIL** ⚠ — `PortalOnboardingWorkflowIntegrationTest` (committed in `67169df`)
calls `POST /api/v1/crm-leads/{id}/convert-to-client` and `POST /api/v1/clients`, which do not exist. With a
database present, at least 2 of its 3 tests will fail. This is a pre-existing test/implementation gap, not an M1 defect.

## 2. Test execution (local, actual)

Command: `$env:JAVA_HOME=$HOME\.jdks\ms-21.0.12.1; .\mvnw.cmd clean test` → **858 run, 0 failures, 0 errors, 43 skipped.**

| Skipped class | Count | Reason |
|---|---|---|
| FlywayMigrationTest | 7 | `@PostgresIntegrationTest`, no `GP_IT_DATABASE_URL` |
| InvoiceLineItemTriggerIntegrationTest | 11 | same |
| BusinessWorkflowIntegrationTest | 7 | same |
| AccountSecurityWorkflowIntegrationTest | 5 | same |
| BusinessTenancyWorkflowIntegrationTest (M1) | 3 | same |
| PortalOnboardingWorkflowIntegrationTest | 3 | same (expected to fail in CI, §1) |
| CrmLeadRepositoryIntegrationTest | 3 | same |
| PostgreSQLIntegrationTest | 1 | same |
| SeedDataCommandTest | 3 | `@EnabledIfEnvironmentVariable(DATABASE_URL)` — stays skipped in CI by design |

**Consequence:** V21 itself has **never been executed** against PostgreSQL. M1 is verified by unit tests,
the HTTP role-matrix test and code review only.

## 3. Database identity and isolation — STOP

| Requirement | Finding |
|---|---|
| Isolated restored database supplied through secure configuration | **Not present.** No `GP_IT_*`, `REHEARSAL*`, `RESTORE*` or `PG*` environment variables in this session |
| Tooling to inspect/restore | `psql`, `pg_restore`, `docker` not installed |
| Production endpoint use | None. The project `.env` points at the shared Render database; it was **not** used. (Tests refuse `render.com` hosts.) |

Per the stop conditions, Phases 3–4 (inventory, counts, V21 execution, backfill rehearsal, rollback, cross-business
tests on restored data) were **not executed**. No record counts are reported because none were measured.

**What is needed to proceed (do not paste credentials in chat):**
1. Restore a Render backup into a separate, non-production PostgreSQL instance (local Docker or a disposable
   Render database in a different project) — the host must not be the production host.
2. Create a dedicated user with rights on that database only.
3. Set `GP_IT_DATABASE_URL`, `GP_IT_DATABASE_USERNAME`, `GP_IT_DATABASE_PASSWORD` (and a `REHEARSAL_DB_URL` for
   the inventory scripts) in the environment of this machine, not in files.
4. Tell me the backup timestamp. I will then verify host identity (must not match the production host),
   `current_database()`, `pg_is_in_recovery()`, the user's privileges and `flyway_schema_history`, before any write.

## 4. M1 implementation review (code inspection)

Reviewed: `V21__create_businesses_and_memberships.sql`, `business/**` (entities, repositories,
`BusinessAccessService`, `BusinessService`, `BusinessMembershipService`, `BusinessController`), SecurityConfig and
GlobalExceptionHandler changes, `SecurityAudit` events, tests.

**Correct as designed**
- Non-members and inactive businesses → 404 (no tenant enumeration); insufficient role → 403.
- Role ranks enforced; only OWNER grants/removes OWNER; ADMIN cannot act on equal/higher roles or itself upward.
- Last-owner protection evaluated **after** a `PESSIMISTIC_WRITE` lock on the business row, so two concurrent
  demotions/removals of different owners cannot both succeed.
- Ownership transfer only by an OWNER, target must be an active member; old owner becomes ADMIN.
- Invitations: 256-bit token, only SHA-256 stored, unique partial index for one pending invite per email,
  row lock on accept, expiry, single use, bound to the invited email, OWNER not invitable, CLIENT accounts refused.
- Every membership/permission change emits a `SECURITY_AUDIT` event with ids only.

**Findings (not fixed — verification-only assignment)**

| Sev | ID | Finding | Recommendation |
|---|---|---|---|
| High | R1 | **Two authorization models coexist.** MVP1 data endpoints still use global roles (`ROLE_ADMIN`, `PERM_*`); business roles do not yet scope any MVP1 data. A global ADMIN sees all MVP1 data; a new business's OWNER sees none of it. | Expected until M2/M3. Do not expose "create business" to anyone outside GrowthPrint staff until tenant-scoped data endpoints exist. |
| Medium | R2 | V21 maps `INVITED` and `SUSPENDED` ADMIN users to **OWNER** of the default business. | Map only `ACTIVE` ADMINs to OWNER, others to ADMIN/MEMBER — a follow-up migration (V22) since V21 must not be edited once applied anywhere. Verify on the restored backup first. |
| Medium | R3 | If no `ACTIVE` ADMIN exists, the default business has **no usable OWNER**. | Rehearsal check: `count(OWNER where user ACTIVE) ≥ 1`; abort M2 otherwise. |
| Low | R4 | The actor's own role is read before the business lock; a concurrent demotion of the actor could let one in-flight action pass with the old role. | Re-read the actor membership after `lockBusiness()` in `changeRole`/`removeMember`/`invite`. |
| Low | R5 | Accepting an invitation when already an active member returns 409 but leaves the invitation pending. | Mark it accepted or revoked in that case. |
| Low | R6 | Concurrent invites for the same email surface as a generic 409 from the unique index. | Acceptable; optionally map to a clearer message. |
| Info | R7 | Audit is log-only. | Persistent audit table in M5 (as planned). |
| Info | R8 | No MANAGER-specific permissions exist yet (MANAGER currently behaves like MEMBER). | Define in M3/M4 with project/task permissions. |

No privilege-escalation path was found: users cannot raise their own role, ADMINs cannot create or remove OWNERs,
and invitations cannot grant OWNER.

## 5. Ownership classification (from migrations V1–V21 — to be confirmed on the restored data)

| Table | Proposed owner column | Source of truth for backfill | Notes |
|---|---|---|---|
| leads (public inquiries) | `business_id` | default business | Public form posts into the default business until per-business forms exist |
| crm_leads | `business_id` | default | FK to leads must share business |
| clients | `business_id` | default | |
| projects, project_milestones | `business_id` | via `client_id` → clients | Composite FK `(business_id, client_id)` |
| support_requests, support_estimates | `business_id` | via client / request | |
| proposals | `business_id` | via client | |
| invoices, invoice_line_items, invoice_payment_reports | `business_id` | via client / invoice | Trigger V14 must keep working |
| outreach_drafts | `business_id` | via crm_lead | |
| client_assignments | `business_id` | via client | Likely superseded by business roles |
| users with role CLIENT | link via `client_id` (no membership) | client's business | Customers, not members |
| ai_settings, ai_usage | `business_id` (later) | default | Global today; per-business in AI milestone |
| users, password_reset_tokens, user_invitations, team_member_permissions | none (global identity) | — | |

Ambiguity to resolve on real data: rows with `NULL client_id` (e.g. internal projects) and orphaned references —
the inventory script must count them before any backfill.

## 6. Recommended M2 sequence

1. Provide the isolated restored database (§3). Verify identity and backup timestamp.
2. Run Flyway to V21 on it; check the default business, OWNER/MEMBER mappings, and that CLIENT users have no membership.
3. Inventory: row counts per table, NULL/orphan FK counts, `NOT VALID` constraint validation (V17).
4. V22 (expand): nullable `business_id` on every table in §5 + correct R2.
5. Backfill in dependency order (clients → projects/support/proposals/invoices → children); compare counts.
6. V23 (contract): `NOT NULL`, composite unique `(business_id, id)`, composite FKs, indexes.
7. Switch repositories/services to tenant-scoped queries behind `BusinessAccessService`; cross-business HTTP tests.
8. Rollback rehearsal: restore the backup again and replay; document timings. Only then request CEO approval
   for production.

## 7. Remaining risks

- CI has never run the database tests; the first run is expected to fail on `PortalOnboardingWorkflowIntegrationTest`.
- V21 has never run on PostgreSQL.
- Production data shape (NULL/orphan references, duplicate V1/V11 DDL effects) is unknown until the restore.
- Uncommitted work in both repositories.
