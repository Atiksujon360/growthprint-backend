# Backend Production-Readiness Audit — 2026-11-01

**Scope:** GrowthPrint backend (Java 17, Spring Boot 4.1.1, PostgreSQL, Flyway, Spring Security/JWT), branch
`feature/backend-production-readiness`. Audit, fixes and tests were done test-first: each confirmed defect got a
regression test that was run and shown failing before the fix, then passing after.

**Environment limits (read first):**
- No isolated PostgreSQL was available (no Docker, no local PostgreSQL, `GP_IT_DATABASE_*` not set). The repo
  `.env` points at the **shared Render database**, which was never used. Every test tagged `postgres` was
  therefore **skipped, not passed**. Migrations V1–V15 have **not** been executed during this audit.
- The frontend (`GrowthPrint-frontend`) was read for contract comparison only; it is integrated in a separate
  session.

## 1. Findings

Legend: **Confirmed** = traced in code and proven by a failing test; **Suspected** = needs PostgreSQL to prove.

### Critical
| ID | Finding | Status | Fix |
|---|---|---|---|
| C1 | `Proposal.deliverables/exclusions/paymentMilestones` were JSONB columns mapped as plain `String` (no `@JdbcTypeCode(SqlTypes.JSON)`); PostgreSQL rejects varchar into jsonb, so creating/updating proposals most likely failed with 500 | Suspected (unit tests mocked the repository) | `@JdbcTypeCode(SqlTypes.JSON)` added (same as `Project`/`OutreachDraft`); guard test + PostgreSQL workflow test `BusinessWorkflowIntegrationTest.proposalLifecycleCreateUpdatePublishAccept` (skipped until a DB exists) |
| C2 | `SeedDataCommand` (profile `seed`) creates `admin@growthprint.dev` with a password that is committed in the repo and logged | Cannot verify without Render access (forbidden) | **Not changed.** Owner must check whether this account exists on Render and rotate/disable it. Recommended: seed passwords from env vars and refuse non-local hosts |

### High (all confirmed)
| ID | Finding | Fix |
|---|---|---|
| H1 | Support request references `GP-REQ-2026-` + random 100–999: only 900 values, hard-coded year → unique-index 500s | `GP-REQ-{UTC year}-{8 hex}`, bounded uniqueness retry |
| H2 | `classify` allowed in any status → second estimate; `support_estimates` has no unique `request_id` and the read expected one row → every list containing that request returned 500 | `classify` only from `NEW`/`NEEDS_CLASSIFICATION`; `attachEstimate` refuses a second estimate; tolerant read `findFirstByRequestIdOrderByCreatedAtDesc`. **No unique index added** (existing data unverified) |
| H3 | No `@Valid` on any of 7 support endpoints (missing fields → NPE 500; enum errors leaked Java class names); client estimate approval stored a client-supplied `approvedBy` | `@Valid` everywhere, `@Size` matching columns, `EnumValues` clean 400 messages; approval records the authenticated user's email |
| H4 | Clients could not report invoice payments (frontend "Pay Now" disabled) | V15 + client payment reporting (section 3). Clients still can never mark invoices paid |
| H5 | No HTTP-level authorization tests; all DB tests were `@Disabled` and stale (wrong migration count, FK violations), and would have migrated **Render** if enabled (`.env` → `DataSourceConfig`) | `RoleMatrixSecurityTest` (always runs) + PostgreSQL harness (`testsupport/`) that overrides `DATABASE_URL`, refuses `render.com`, uses a random per-run schema dropped afterwards |
| H6 | `JwtAuthenticationFilter` (a `@Component`) was also auto-registered as a standalone servlet filter. When that copy runs before the security chain, every authenticated request is 401. Production worked only because of servlet filter ordering | Standalone registration disabled (`FilterRegistrationBean#setEnabled(false)`); found by the role-matrix test (97/160 failing → 160/160 passing) |

### Medium
| Finding | Fix |
|---|---|
| Proposal numbers `count()+1` with a `while(exists)` loop that never changed the number → infinite loop on gaps | Bounded forward allocation; concurrent races → 409 |
| Proposal not found → 400; other client's proposal → 409 | 404 / 403 |
| Expired proposal set to `EXPIRED` then exception → change rolled back | `ProposalExpiredException` + `noRollbackFor` (409); proven only by the PostgreSQL test |
| Invoice could be created as `PAID` (no `paid_at`), `VOID`, `OVERDUE`; unknown client reported as "invoice not found" | Only `DRAFT`/`ISSUED`; due date ≥ issue date; `ClientNotFoundException` (404) |
| Unique/FK/check violations (e.g. duplicate CRM lead company+email) → 500 | 409 "The request conflicts with existing data" (SQL logged, never returned) |
| `PATCH /clients/{id}` took a raw `Map`: frontend key `phone` silently ignored (backend read `contactPhone`), non-string → 500 | Typed `UpdateClientCommand` (`phone`, alias `contactPhone`), `@Valid` |
| DTO limits did not match columns → DB errors as 500 | `@Size`/`@Min`/`@Max`/`@PositiveOrZero`/`@Email` aligned with columns |
| Errors not RFC 9457 | Body now has `type/title/status/detail/instance` **plus** legacy `timestamp/error/message/path/fieldErrors`; still `application/json` because the frontend only parses that content type |
| N+1 queries on list endpoints (support: 3/row, invoices and projects: 1/row) | Batch loading; response shapes unchanged |
| Outreach re-approve rewrote `approvedAt` | Idempotent |

### Documented, not changed (need a business decision)
- Admin `PATCH /support-requests/{id}/status` can move `ESTIMATE_PENDING → AWAITING_CLIENT_APPROVAL` without an
  estimate and `AWAITING_CLIENT_APPROVAL → READY_TO_START` without client approval.
- Support estimates are always USD, even for EUR/GBP clients.
- No optimistic locking (`@Version`); concurrent edits are last-write-wins.
- JWTs stay valid up to 1 h after an account is disabled or its role changes.
- Invoice `clientName`/`clientEmail` are taken from the request, not the client record.
- No pagination on list endpoints (adding it changes the list contract; coordinate with the frontend).
- Automations (`/automations`) and `/health/services` are called by the frontend but have no domain model or
  business rules: **out of scope**, not implemented.
- In-memory rate limiter is per instance.

## 2. Defaults chosen for unanswered decisions
- Payment reports are a separate append-only table (not a new invoice status).
- No unique index on `support_estimates(request_id)` (existing data unverified); application guard only.
- Client estimate approval records the authenticated user's email; `approvedBy` is still accepted (required by
  validation) but ignored on the client path. The admin path keeps the submitted name (approval on the client's
  behalf).
- Invoices can only be created as `DRAFT` or `ISSUED`.
- `spring-boot-starter-webmvc-test` (test scope, Boot-managed) added for MockMvc tests.

## 3. New API: client payment reporting (V15)
| Method | Path | Role | Result |
|---|---|---|---|
| POST | `/api/v1/invoices/my/{id}/reconcile` | CLIENT | 201 PENDING report; invoice unchanged. 404 not yours, 409 not ISSUED/OVERDUE or already pending |
| GET | `/api/v1/invoices/my/{id}/payment-reports` | CLIENT | own reports |
| GET | `/api/v1/invoices/{id}/payment-reports` | ADMIN | reports |
| POST | `/api/v1/invoices/payment-reports/{reportId}/verify` | ADMIN | marks invoice PAID (same rule as `/reconcile`) |
| POST | `/api/v1/invoices/payment-reports/{reportId}/reject` | ADMIN | note required; invoice unchanged |

Request: `{amount, paymentMethod (BANK_TRANSFER|CARD|CHECK|OTHER), reference?, paidOn, note?}`; currency comes
from the invoice. V15 enforces amount > 0, allowed values, review consistency, and at most one PENDING report per
invoice (partial unique index). The existing admin `POST /api/v1/invoices/{id}/reconcile` is unchanged.

## 4. Test evidence
| Step | Command | Result |
|---|---|---|
| Baseline (before any change) | `.\mvnw.cmd -B clean verify` | 200 run, 0 failures, 0 errors, 25 skipped, BUILD SUCCESS |
| Regression tests before fixes | `-Dtest=SupportRequestDefectRegressionTest,ProposalDefectRegressionTest,CrossModuleDefectRegressionTest` | 21 run, **20 failed** (1 control passed) |
| Regression tests after fixes | same | 21 run, 0 failed |
| Payment reports, skeleton | `-Dtest=InvoicePaymentReportServiceTest` | 15 run, 10 failures + 5 errors |
| Payment reports, implemented | same | 15 run, 0 failed |
| Role matrix, filter double-registered | `-Dtest=RoleMatrixSecurityTest` | 160 run, **97 failed** (all authenticated requests 401) |
| Role matrix, after H6 fix | same | 160 run, 0 failed |
| Final | `.\mvnw.cmd -B clean verify` | 411 run, 0 failures, 0 errors, 32 skipped, jar built, BUILD SUCCESS. Skipped = all DB tests: FlywayMigrationTest 7, InvoiceLineItemTriggerIntegrationTest 11, BusinessWorkflowIntegrationTest 7, CrmLeadRepositoryIntegrationTest 3, PostgreSQLIntegrationTest 1, SeedDataCommandTest 3 |
| Final, PostgreSQL group | `.\mvnw.cmd -B test -Dgroups=postgres` | 29 run, **29 skipped** (no isolated DB configured). Migrations V1–V15, the proposal JSONB fix (C1), expired-proposal persistence and the payment-report flow are therefore **not yet verified on PostgreSQL** |

Note: the final run also includes tests added in parallel by another session (`config/JwtProperties*Test`,
`JwtConfigurationIntegrationTest`); they pass but are not part of this audit.

Existing tests changed only where they asserted the old defective behaviour (each change carries an
"Audit 2026-11-01" comment): `InvoiceServiceTest`, `ProposalServiceTest`, `SupportRequestServiceTest`,
`ProjectServiceTest`, `ClientControllerTest`.

## 5. How to run the PostgreSQL tests (isolated DB only)
Set, in the shell environment (not in `.env`): `GP_IT_DATABASE_URL` (Neon `growthprint-mvp-test`, **direct**
endpoint, not the pooler), `GP_IT_DATABASE_USERNAME`, `GP_IT_DATABASE_PASSWORD`. Then:
`.\mvnw.cmd -B test -Dgroups=postgres`. Each run migrates V1–V15 into a new `gp_it_<random>` schema and drops it
at JVM exit. `render.com` hosts are refused.
