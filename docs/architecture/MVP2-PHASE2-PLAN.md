# MVP2 Phase 2 — Backend Plan (in progress, no code written yet)

**Status 2026-11-08:** Phase 1 (frontend) approved. The CEO gave no preference on the open decisions, so the recommended
defaults apply: (1) existing clients and leads are backfilled into the default business; (2) conversion is also blocked
for DISCOVERED, RESEARCHING and NEEDS_REVIEW in the workspace flow (the MVP1 studio flow is unchanged); (3) business
creation stays staff-only; (4) invitations stay copy-link only; (5) only Owners remove Admins.

Branch `feature/mvp2-m1-security-hardening`. M1 was committed in `c54752b`, and 6 review-fix files are still staged
(do not discard them). Latest migration: **V23**. The next one is **V24**.

## Findings from reading the code
- `BusinessAccessService.require(businessId, userId, minRole)` already gives 404 to non-members and 403 to roles that are too low. Reuse it everywhere.
- Membership and invitation rules (last owner, rank checks, email-bound, inviter re-check) already exist in `BusinessMembershipService`.
- The default business id is fixed: `Business.DEFAULT_BUSINESS_ID` = `6a1e0b7c-0000-4000-8000-00000000b001`.
- `clients` has no `created_at` column, and `crm_leads` has no `estimated_value` column.
- MVP1 services rebuild entities with constructors and `save` (merge). New columns must be `updatable=false`, or carried over in `CrmLead.carryConversionFrom`, or they will be wiped.
- Error mapping: `IllegalStateException` gives 409, `IllegalArgumentException` gives 400, and validation errors return `fieldErrors` (check `GlobalExceptionHandler`).
- `SecurityConfig` already allows `/api/v1/businesses/**` for authenticated users. `/api/v1/business-invitations/preview` and `/decline` still need rules.
- Lead source enums differ: the backend has PUBLIC_DIRECTORY and TECH_AUDIT_DISCOVERY, the frontend mock has OUTBOUND, EVENT and OTHER. The frontend will adopt the backend values in Phase 3.

## V24 (forward-only, additive)
1. `businesses`: add `industry`, `team_size` (CHECK SOLO/SMALL/MEDIUM/LARGE), `description` (500), `website`, `timezone`, and `currency` (NOT NULL DEFAULT 'USD', CHECK USD/EUR/GBP).
2. `clients`: add `business_id UUID NOT NULL DEFAULT '<default id>' REFERENCES businesses ON DELETE RESTRICT` and `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Add `UNIQUE (id, business_id)` and an index on `(business_id, name)`.
3. `crm_leads`: add `business_id` the same way, plus `estimated_value NUMERIC(12,2) CHECK >= 0`. Add indexes on `(business_id, pipeline_stage)` and `(business_id, updated_at DESC)`. Add the composite FK `(converted_client_id, business_id) → clients(id, business_id)`, so a lead can never convert into another business's client.
4. `business_invitations`: allow status DECLINED and add `declined_at`.
5. New table `business_activities`: id, business_id (FK, cascade), type (CHECK), message (300), actor_user_id, actor_name, link, created_at. Index on `(business_id, created_at DESC)`.

## Endpoints (new package `workspace`, thin controllers, `BusinessAccessService` first)
| Route | Minimum role |
|---|---|
| `GET /businesses/{id}/clients?search&status&page&pageSize` returns `{items,page,pageSize,total}` (page size 1–100, default 10) | MEMBER |
| `POST`, `PATCH /businesses/{id}/clients[/{clientId}]`, `GET …/{clientId}` | MANAGER (GET: MEMBER) |
| `GET /businesses/{id}/leads?search&stage|group&page&pageSize`, `GET …/{leadId}`, `POST` | MEMBER |
| `PATCH …/leads/{leadId}`, `PUT …/{leadId}/stage`, `POST …/{leadId}/convert-to-client` | MANAGER |
| `DELETE …/leads/{leadId}` (409 if converted) | ADMIN |
| `GET /businesses/{id}/dashboard`, `GET /businesses/{id}/activity` | MEMBER |
| `PATCH /businesses/{id}` extended with the profile fields | ADMIN |
| `GET /business-invitations/preview?token=` (auth, email-bound), `POST /business-invitations/decline` | authenticated |

Every query filters on `business_id`. Lookups use `findByIdAndBusinessId`, so a wrong business returns 404. The MVP1 global
`/clients` and `/crm-leads` endpoints are limited to the default business.

## Tests to add
Unit tests for each new service (cross-business 404, role 403s, convert once, stricter eligibility, pagination clamps,
dashboard maths), a controller role matrix, and a PostgreSQL integration test for V24. The integration test needs `GP_IT_DATABASE_URL`
pointing at an isolated database, **never Render**. Report it as skipped if no database is available.
Then run `mvnw clean test` (JDK 21) and write `MVP2-BACKEND-COMPLETION-REPORT.md`.
