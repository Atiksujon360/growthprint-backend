# GrowthPrint Backend API Inventory & Implementation Plan

**Document Version:** 1.0  
**Date:** 2026-10-23  
**Audit Type:** Comprehensive Backend API Inventory and Gap Analysis  
**Scope:** Inspection-only, no implementation in this phase

---

## Executive Summary

**Current State:**
The GrowthPrint backend has a solid foundation with Spring Boot 4.1.1, Java 17, PostgreSQL, and Flyway migrations. Four functional modules are implemented: public inquiries, raw inquiries (leads), clients, and CRM leads. The frontend is a Next.js 16 application with React 19, TypeScript 7, and Redux Toolkit, featuring comprehensive admin and portal interfaces with mock data support.

**Most Critical Risks:**
1. **No Authentication/Authorization:** Admin endpoints are disabled by a feature flag (`app.admin-endpoints.enabled=false`) rather than proper security. When enabled, they have no authentication or authorization.
2. **Missing Core Business APIs:** Projects, tasks, support requests, proposals, invoices, outreach, automations, activities, and user management have no backend implementation.
3. **No CORS Configuration:** Cross-origin restrictions not configured, which may block frontend API calls in production.
4. **Database Integration Tests Blocked:** 11 of 32 tests are skipped because Docker is unavailable in the test environment, preventing verification of migrations and repository operations.
5. **Mock Mode Masking Integration Gaps:** Frontend defaults to mock mode, potentially hiding API contract mismatches and runtime errors.

**Primary Gap:**
The frontend has extensive business logic for projects, support tickets, proposals, invoices, outreach, and automations—entirely operating on mock data in Redux state. The backend has no corresponding persistence layer for these modules. Only public inquiries, raw inquiries, clients, and CRM leads have backend implementations.

---

## Repository Access and Inspection Scope

**Backend Repository:**
- Path: `C:\Users\Hi\OneDrive\Desktop\growthprint-backend\growthprint-backend`
- Access: Full local filesystem access
- Inspected: All Java source files, configuration files, migrations, and tests

**Frontend Repository:**
- Path: `C:\Users\Hi\OneDrive\Desktop\GrowthPrint-frontend`
- Access: Full local filesystem access
- Inspected: All TypeScript/TSX files, API client, Redux slices, mock data, and components

**Limitations:**
- Docker unavailable in test environment (blocks database integration tests)
- No direct access to production PostgreSQL database (schema verification limited to migration files)
- Cannot verify actual runtime behavior without enabling admin endpoints (security risk)

---

## Verified Technology Stack

### Backend

**Framework and Language:**
- Spring Boot: 4.1.1 (verified from pom.xml parent version)
- Java: 17 (verified from pom.xml property)
- Maven build system

**Core Dependencies:**
- spring-boot-starter-webmvc
- spring-boot-starter-data-jpa
- spring-boot-starter-validation
- spring-boot-starter-actuator
- spring-boot-starter-flyway
- springdoc-openapi-starter-webmvc-ui: 3.1.1
- springboot4-dotenv: 5.1.0 (optional)
- postgresql (runtime)
- flyway-database-postgresql

**Test Dependencies:**
- spring-boot-starter-test
- spring-boot-testcontainers
- testcontainers: 1.20.4
- testcontainers-postgresql
- testcontainers-junit-jupiter

**Database:**
- PostgreSQL (connection via DATABASE_URL environment variable)
- Flyway migrations enabled
- JPA DDL mode: validate (schema changes via migrations only)

**API Documentation:**
- SpringDoc OpenAPI 3.1.1
- Swagger UI enabled at /swagger-ui.html
- OpenAPI docs at /v3/api-docs

**Actuator:**
- Health and info endpoints exposed
- Health details disabled (show-details: never)

### Frontend

**Framework and Language:**
- Next.js: 16.4.2
- React: 19.0.1
- TypeScript: 7.0.2
- Node.js dependencies verified from package.json

**State Management:**
- Redux Toolkit: 2.13.0
- React Redux: 9.3.0

**UI and Styling:**
- Tailwind CSS: 4.3.3
- Lucide React: 0.546.0 (icons)
- Recharts: 3.10.1 (charts)
- Motion: 12.23.24 (animations)

**Forms and Validation:**
- React Hook Form: 7.90.0
- Zod: 4.6.7

**Build Tools:**
- TypeScript compiler with --noEmit for linting
- Node.js native test runner with --experimental-strip-types

---

## Existing Backend Endpoint Inventory

### Implemented Endpoints

| Module | HTTP Method | Endpoint | Controller | Status | Auth Required | Tests |
|--------|-------------|----------|------------|--------|---------------|-------|
| Public Inquiries | POST | /api/v1/public/inquiries | PublicInquiryController | IMPLEMENTED, VERIFICATION INCOMPLETE | No | Validation test only |
| Clients | GET | /api/v1/clients | ClientController | IMPLEMENTED, VERIFICATION INCOMPLETE | No | 3/3 controller tests pass |
| Clients | GET | /api/v1/clients/{id} | ClientController | IMPLEMENTED, VERIFICATION INCOMPLETE | No | Included in controller tests |
| Raw Inquiries (Admin) | GET | /api/v1/leads | AdminLeadController | IMPLEMENTED, BLOCKED | Yes (feature flag) | No dedicated tests |
| CRM Leads (Admin) | GET | /api/v1/crm-leads | CrmLeadController | IMPLEMENTED, BLOCKED | Yes (feature flag) | 6/6 service tests pass |
| CRM Leads (Admin) | GET | /api/v1/crm-leads/{id} | CrmLeadController | IMPLEMENTED, BLOCKED | Yes (feature flag) | Covered by service tests |
| CRM Leads (Admin) | POST | /api/v1/crm-leads | CrmLeadController | IMPLEMENTED, BLOCKED | Yes (feature flag) | Covered by service tests |
| CRM Leads (Admin) | POST | /api/v1/crm-leads/inquiry/{inquiryId}/convert | CrmLeadController | IMPLEMENTED, BLOCKED | Yes (feature flag) | Covered by service tests |
| CRM Leads (Admin) | PUT | /api/v1/crm-leads/{id}/pipeline-stage | CrmLeadController | IMPLEMENTED, BLOCKED | Yes (feature flag) | Covered by service tests |

**Total Implemented Endpoints:** 9  
**Total Enabled Without Docker:** 3 (public inquiries, clients)  
**Total Disabled by Feature Flag:** 6 (admin endpoints)

### Controller Details

**ClientController** (`src/main/java/com/growthprint/backend/client/controller/ClientController.java`)
- `GET /api/v1/clients` - Returns List<ClientResponse>
- `GET /api/v1/clients/{id}` - Returns ClientResponse
- No pagination, filtering, or search
- No update/delete operations
- Status: Read-only implementation

**CrmLeadController** (`src/main/java/com/growthprint/backend/crm/lead/controller/CrmLeadController.java`)
- Condition: `@ConditionalOnProperty(name = "app.admin-endpoints.enabled", havingValue = "true")`
- `GET /api/v1/crm-leads` - Returns List<CrmLeadResponse>
- `GET /api/v1/crm-leads/{id}` - Returns CrmLeadResponse
- `POST /api/v1/crm-leads` - Creates CRM lead from CreateCrmLeadCommand
- `POST /api/v1/crm-leads/inquiry/{inquiryId}/convert` - Converts inquiry to CRM lead
- `PUT /api/v1/crm-leads/{id}/pipeline-stage` - Updates pipeline stage
- Tag: "CRM Leads (Development admin)"
- No delete operation (adapter exists but not implemented)

**PublicInquiryController** (`src/main/java/com/growthprint/backend/inquiry/controller/PublicInquiryController.java`)
- `POST /api/v1/public/inquiries` - Submits public inquiry
- Returns 201 with InquiryAcceptedResponse (referenceNumber, status, receivedAt)
- Validated via @Valid on PublicInquiryRequest
- Converts inquiry to Lead entity via LeadService.createFromInquiry()
- Tag: "Public inquiries"

**AdminLeadController** (`src/main/java/com/growthprint/backend/lead/controller/AdminLeadController.java`)
- Condition: `@ConditionalOnProperty(name = "app.admin-endpoints.enabled", havingValue = "true")`
- `GET /api/v1/leads` - Returns List<LeadResponse> (raw inquiries)
- Tag: "Development admin leads"
- No CRUD operations beyond list
- Status: Minimal implementation

---

## Frontend API Consumer Inventory

### API Client Structure

**File:** `lib/api/client.ts`

**Implemented Adapters:**
```typescript
agencyApi = {
  leads: {
    getAll: () => apiClient.get("/crm-leads"),           // Points to CRM leads
    getById: (id) => apiClient.get(`/crm-leads/${id}`),
    create: (lead) => apiClient.post("/crm-leads", lead),
    updateStage: (id, stage) => apiClient.put(`/crm-leads/${id}/pipeline-stage`, { pipelineStage: stage }),
    delete: (id) => apiClient.delete(`/crm-leads/${id}`), // NOT IMPLEMENTED
  },
  clients: {
    getAll: () => apiClient.get("/clients"),
    getById: (id) => apiClient.get(`/clients/${id}`),
    update: (id, data) => apiClient.patch(`/clients/${id}`, data), // NOT IMPLEMENTED
  },
  projects: {
    getAll: () => apiClient.get("/projects"),              // NOT IMPLEMENTED
    getById: (id) => apiClient.get(`/projects/${id}`),     // NOT IMPLEMENTED
    updateStatus: (id, status) => apiClient.patch(`/projects/${id}/status`, { status }), // NOT IMPLEMENTED
    createMilestone: (projectId, milestone) => apiClient.post(`/projects/${projectId}/milestones`, milestone), // NOT IMPLEMENTED
  },
  support: {
    getAll: () => apiClient.get("/support-requests"),      // NOT IMPLEMENTED
    create: (data) => apiClient.post("/support-requests", data), // NOT IMPLEMENTED
    classify: (id, classification) => apiClient.post(`/support-requests/${id}/classify`, classification), // NOT IMPLEMENTED
    attachEstimate: (id, estimate) => apiClient.post(`/support-requests/${id}/estimate`, estimate), // NOT IMPLEMENTED
    approveEstimate: (id, approvedBy) => apiClient.post(`/support-requests/${id}/approve-estimate`, { approvedBy }), // NOT IMPLEMENTED
  },
  invoices: {
    getAll: () => apiClient.get("/invoices"),             // NOT IMPLEMENTED
    getById: (id) => apiClient.get(`/invoices/${id}`),      // NOT IMPLEMENTED
    markPaid: (id, details) => apiClient.post(`/invoices/${id}/reconcile`, details), // NOT IMPLEMENTED
  },
  outreach: {
    getDrafts: () => apiClient.get("/outreach/drafts"),    // NOT IMPLEMENTED
    approveDraft: (id) => apiClient.post(`/outreach/drafts/${id}/approve`), // NOT IMPLEMENTED
    sendDraft: (id) => apiClient.post(`/outreach/drafts/${id}/send`), // NOT IMPLEMENTED
  },
  automations: {
    getAll: () => apiClient.get("/automations"),            // NOT IMPLEMENTED
    trigger: (id) => apiClient.post(`/automations/${id}/run`), // NOT IMPLEMENTED
  },
  systemHealth: {
    checkAll: () => apiClient.get("/health/services"),      // NOT IMPLEMENTED
  }
}
```

**Mock Mode Behavior:**
- Controlled by `NEXT_PUBLIC_USE_MOCK_API` environment variable
- Default: `true` (mock mode enabled unless explicitly set to 'false')
- When mock mode is active, API calls return no data (no mock adapter implemented)
- Frontend relies on Redux initialData.ts for mock data when in mock mode

### Redux Integration

**File:** `features/agency/agencySlice.ts`

**Implemented Async Thunks:**
- `fetchClients()` - Calls agencyApi.clients.getAll(), stores in state.clients
- `fetchLeads()` - Calls agencyApi.leads.getAll(), transforms backend score components into qualificationBreakdown
- `fetchClientById()` - Calls agencyApi.clients.getById(id)

**Local Redux Actions (No Backend):**
- `submitPublicInquiry` - Creates lead in Redux state only (no API call)
- `createSupportRequest` - Creates request in Redux state only
- `classifyRequest` - Updates request status in Redux state only
- `attachEstimate` - Adds estimate to request in Redux state only
- `clientApproveEstimate` - Updates request, creates task and invoice in Redux state only
- `updateRequestStatus` - Updates request status in Redux state only
- `approveOutreachDraft` - Updates draft status and lead pipeline stage in Redux state only
- `sendApprovedOutreach` - Updates draft status and lead pipeline stage in Redux state only
- `updateOutreachDraft` - Updates draft content in Redux state only
- `updateLeadStage` - Updates lead pipeline stage in Redux state only
- `triggerWorkflowRun` - Updates workflow run status in Redux state only
- `payInvoice` - Updates invoice status in Redux state only
- `toggleTaskStatus` - Toggles task status in Redux state only

### Frontend Screens and Backend Dependencies

**Admin Screens:**

| Screen | File | Data Source | Backend Status | Required Backend |
|--------|------|-------------|----------------|-------------------|
| Dashboard | AdminDashboard.tsx | Redux state (all entities) | PARTIAL | Aggregation endpoints |
| Leads | AdminLeadsView.tsx | Redux + fetchLeads thunk | IMPLEMENTED, BLOCKED | ✅ CRM Leads exists |
| Clients | AdminClientsView.tsx | Redux + fetchClients thunk | IMPLEMENTED, BLOCKED | ✅ Clients exists |
| Projects | AdminProjectsView.tsx | Redux state only | MISSING | Projects CRUD, milestones |
| Requests Queue | AdminRequestsQueue.tsx | Redux state only | MISSING | Support CRUD, estimates |
| Invoices | AdminInvoicesView.tsx | Redux state only | MISSING | Invoices CRUD, payments |
| Outreach Studio | AdminOutreachStudio.tsx | Redux state only | MISSING | Outreach CRUD, send |
| Automation | AdminAutomationView.tsx | Redux state only | MISSING | Automations CRUD, trigger |
| Activity Log | AdminActivityView.tsx | Redux state only | MISSING | Activity events |

**Portal Screens:**

| Screen | File | Data Source | Backend Status | Required Backend |
|--------|------|-------------|----------------|-------------------|
| Portal Dashboard | PortalDashboard.tsx | Redux state (filtered by client) | MISSING | Client-scoped data |
| Projects | PortalProjectsView.tsx | Redux state (filtered by client) | MISSING | Client projects |
| Proposals | PortalProposalsView.tsx | Redux state (filtered by client) | MISSING | Client proposals |
| Invoices | PortalInvoicesView.tsx | Redux state (filtered by client) | MISSING | Client invoices |
| Requests | PortalRequestsView.tsx | Redux state (filtered by client) | MISSING | Client support requests |
| Messages | PortalMessagesView.tsx | Redux state | MISSING | Messages/notifications |

**Public Screens:**

| Screen | File | Data Source | Backend Status | Required Backend |
|--------|------|-------------|----------------|-------------------|
| Public Site (all components) | Multiple | Static + QuoteInquiryModal | PARTIAL | ✅ Public inquiry exists |

---

## Complete API Gap Matrix

### Required Master Table

| Module | Operation | HTTP Method | Proposed/Current Endpoint | Frontend Consumer | Backend Status | Security Requirement | Database Dependency | Tests Required | Priority |
|--------|-----------|-------------|---------------------------|-------------------|----------------|---------------------|-------------------|---------------|----------|
| **Authentication** | Login | POST | /api/v1/auth/login | authSlice.ts | MISSING | Required | users table | Unit, integration | P0 |
| **Authentication** | Logout | POST | /api/v1/auth/logout | authSlice.ts | MISSING | Required | users table | Unit, integration | P0 |
| **Authentication** | Current user | GET | /api/v1/auth/me | authSlice.ts | MISSING | Required | users table | Unit, integration | P0 |
| **Clients** | List all | GET | /api/v1/clients | AdminClientsView.tsx | IMPLEMENTED, BLOCKED | Required | clients table | Integration with Docker | P1 |
| **Clients** | Get by ID | GET | /api/v1/clients/{id} | AdminClientsView.tsx | IMPLEMENTED, BLOCKED | Required | clients table | Integration with Docker | P1 |
| **Clients** | Update | PATCH | /api/v1/clients/{id} | agencyApi.clients.update | MISSING | Required | clients table | Unit, integration | P2 |
| **CRM Leads** | List all | GET | /api/v1/crm-leads | AdminLeadsView.tsx | IMPLEMENTED, BLOCKED | Required | crm_leads table | Integration with Docker | P1 |
| **CRM Leads** | Get by ID | GET | /api/v1/crm-leads/{id} | agencyApi.leads.getById | IMPLEMENTED, BLOCKED | Required | crm_leads table | Integration with Docker | P1 |
| **CRM Leads** | Create | POST | /api/v1/crm-leads | agencyApi.leads.create | IMPLEMENTED, BLOCKED | Required | crm_leads, leads tables | Integration with Docker | P2 |
| **CRM Leads** | Update pipeline stage | PUT | /api/v1/crm-leads/{id}/pipeline-stage | agencyApi.leads.updateStage | IMPLEMENTED, BLOCKED | Required | crm_leads table | Integration with Docker | P2 |
| **CRM Leads** | Delete | DELETE | /api/v1/crm-leads/{id} | agencyApi.leads.delete | MISSING | Required | crm_leads table | Unit, integration | P3 |
| **Raw Inquiries** | List all | GET | /api/v1/leads | AdminLeadController | IMPLEMENTED, BLOCKED | Required | leads table | Integration with Docker | P2 |
| **Public Inquiries** | Submit | POST | /api/v1/public/inquiries | QuoteInquiryModal.tsx | IMPLEMENTED, VERIFICATION INCOMPLETE | None | leads table | Integration with Docker | P1 |
| **Projects** | List all | GET | /api/v1/projects | AdminProjectsView.tsx | MISSING | Required | projects table | Unit, integration | P2 |
| **Projects** | Get by ID | GET | /api/v1/projects/{id} | agencyApi.projects.getById | MISSING | Required | projects table | Unit, integration | P2 |
| **Projects** | Create | POST | /api/v1/projects | agencySlice (local only) | MISSING | Required | projects, clients tables | Unit, integration | P2 |
| **Projects** | Update status | PATCH | /api/v1/projects/{id}/status | agencyApi.projects.updateStatus | MISSING | Required | projects table | Unit, integration | P2 |
| **Projects** | Create milestone | POST | /api/v1/projects/{id}/milestones | agencyApi.projects.createMilestone | MISSING | Required | project_milestones table | Unit, integration | P3 |
| **Tasks** | List all | GET | /api/v1/tasks | agencySlice (local only) | MISSING | Required | tasks table | Unit, integration | P2 |
| **Tasks** | Update status | PATCH | /api/v1/tasks/{id}/status | agencySlice.toggleTaskStatus | MISSING | Required | tasks table | Unit, integration | P2 |
| **Support Requests** | List all | GET | /api/v1/support-requests | AdminRequestsQueue.tsx | MISSING | Required | support_requests table | Unit, integration | P2 |
| **Support Requests** | Create | POST | /api/v1/support-requests | agencyApi.support.create | MISSING | Required | support_requests, clients tables | Unit, integration | P2 |
| **Support Requests** | Classify | POST | /api/v1/support-requests/{id}/classify | agencyApi.support.classify | MISSING | Required | support_requests table | Unit, integration | P2 |
| **Support Requests** | Attach estimate | POST | /api/v1/support-requests/{id}/estimate | agencyApi.support.attachEstimate | MISSING | Required | support_estimates table | Unit, integration | P2 |
| **Support Requests** | Approve estimate | POST | /api/v1/support-requests/{id}/approve-estimate | agencyApi.support.approveEstimate | MISSING | Required | support_estimates table | Unit, integration | P2 |
| **Support Requests** | Update status | PATCH | /api/v1/support-requests/{id}/status | agencySlice.updateRequestStatus | MISSING | Required | support_requests table | Unit, integration | P2 |
| **Proposals** | List all | GET | /api/v1/proposals | agencySlice (local only) | MISSING | Required | proposals table | Unit, integration | P3 |
| **Proposals** | Create | POST | /api/v1/proposals | agencySlice (local only) | MISSING | Required | proposals, clients tables | Unit, integration | P3 |
| **Invoices** | List all | GET | /api/v1/invoices | AdminInvoicesView.tsx | MISSING | Required | invoices table | Unit, integration | P2 |
| **Invoices** | Get by ID | GET | /api/v1/invoices/{id} | agencyApi.invoices.getById | MISSING | Required | invoices table | Unit, integration | P2 |
| **Invoices** | Mark paid | POST | /api/v1/invoices/{id}/reconcile | agencyApi.invoices.markPaid | MISSING | Required | invoices table | Unit, integration | P2 |
| **Outreach** | List drafts | GET | /api/v1/outreach/drafts | AdminOutreachStudio.tsx | MISSING | Required | outreach_drafts table | Unit, integration | P3 |
| **Outreach** | Approve draft | POST | /api/v1/outreach/drafts/{id}/approve | agencyApi.outreach.approveDraft | MISSING | Required | outreach_drafts, crm_leads tables | Unit, integration | P3 |
| **Outreach** | Send draft | POST | /api/v1/outreach/drafts/{id}/send | agencyApi.outreach.sendDraft | MISSING | Required | outreach_drafts, crm_leads tables | Unit, integration | P3 |
| **Automations** | List all | GET | /api/v1/automations | AdminAutomationView.tsx | MISSING | Required | workflows table | Unit, integration | P3 |
| **Automations** | Trigger | POST | /api/v1/automations/{id}/run | agencyApi.automations.trigger | MISSING | Required | workflows table | Unit, integration | P3 |
| **Activities** | List all | GET | /api/v1/activities | AdminActivityView.tsx | MISSING | Required | activities table | Unit, integration | P3 |
| **Health** | Service health | GET | /api/v1/health/services | agencyApi.systemHealth.checkAll | MISSING | None | None | Unit | P2 |

**Status Legend:**
- IMPLEMENTED, VERIFICATION INCOMPLETE: Source exists, but requires Docker/integration testing
- IMPLEMENTED, BLOCKED: Source exists but disabled by feature flag; requires auth first
- MISSING: No implementation exists
- PARTIAL: Some operations exist but incomplete

**Priority Legend:**
- P0: Critical (blocks production deployment)
- P1: High (core business functionality)
- P2: Medium (important but not blocking)
- P3: Low (nice-to-have)

---

## Database and Migration Assessment

### Existing Schema

**Migration V1** (`V1__initial_schema.sql`)
- Purpose: Foundation migration (no business tables)
- Content: Comments only
- Status: Completed

**Migration V2** (`V2__create_leads.sql`)
- Table: `leads` (raw public inquiries)
- Columns: 17 columns including reference_number, status, consent
- Constraints: consent must be TRUE
- Indexes: received_at DESC, business_email, status
- Status: Completed

**Migration V3** (`V3__create_clients.sql`)
- Table: `clients`
- Columns: 14 columns including retainer tier, consumed hours, billing currency
- Constraints: status, retainer_tier, billing_currency enum checks
- Indexes: status, account_manager, contract_start_date
- Status: Completed

**Migration V4** (`V4__create_crm_leads.sql`)
- Table: `crm_leads` (qualified sales prospects)
- Columns: 27 columns including qualification scores, pipeline stage
- Constraints: source, target_service, pipeline_stage enum checks; qualification score range checks; unique constraint on (company_name, contact_email)
- Indexes: pipeline_stage, qualification_score, source, inquiry_id
- Foreign Key: inquiry_id → leads.id (ON DELETE SET NULL)
- Status: Completed (applied to production database per previous session notes)

### Schema Gaps

**Missing Tables:**
- `users` - Authentication and user profiles
- `projects` - Project tracking
- `project_milestones` - Project milestones
- `tasks` - Task management
- `support_requests` - Support ticketing
- `support_estimates` - Support request estimates
- `proposals` - Sales proposals
- `invoices` - Billing invoices
- `invoice_line_items` - Invoice line items
- `outreach_drafts` - Outreach email drafts
- `workflows` - Automation workflows
- `activities` - Audit/event log
- `notifications` - User notifications

**Missing Relationships:**
- Projects → Clients (many-to-one)
- Tasks → Projects (many-to-one)
- Support Requests → Clients (many-to-one)
- Support Requests → Projects (optional many-to-one)
- Support Estimates → Support Requests (one-to-one)
- Invoices → Clients (many-to-one)
- Proposals → Clients (many-to-one)
- Outreach Drafts → CRM Leads (many-to-one)
- Activities → All entities (audit trail)

### Verification Limitations

**Without Docker:**
- Cannot verify V4 migration applies correctly to fresh database
- Cannot verify foreign key constraints enforce correctly
- Cannot verify CHECK constraints enforce enum values and score ranges
- Cannot verify indexes exist and perform as expected
- Cannot verify repository operations against real PostgreSQL

**With Production Database:**
- V4 migration verified against Render PostgreSQL 18.6 in previous session
- Schema version 4 reported as up-to-date
- **This is NOT a substitute for test database verification**

---

## Security Findings

### Critical (P0)

**1. No Authentication/Authorization:**
- **Finding:** Admin endpoints (`/api/v1/leads`, `/api/v1/crm-leads`) are disabled by `app.admin-endpoints.enabled=false` feature flag in application.yml
- **Risk:** When enabled, endpoints have no authentication or authorization. Anyone with URL access can list, create, update, and delete sensitive business data.
- **Evidence:** No `@EnableWebSecurity`, `SecurityConfig`, or `WebSecurityConfig` classes found. No Spring Security dependency in pom.xml.
- **Impact:** Production deployment blocked until authentication implemented.
- **Required:** Spring Security with JWT/OAuth2 before enabling admin endpoints.

**2. No CORS Configuration:**
- **Finding:** No `@CrossOrigin` annotations or CORS configuration found in controllers or global configuration.
- **Risk:** Frontend API calls may be blocked by browser CORS policy in production (different origins).
- **Evidence:** Grep for `@CrossOrigin` returned no matches.
- **Impact:** Frontend-backend communication may fail in production deployment.
- **Required:** Add CORS configuration for frontend origin.

### High (P1)

**3. Missing Rate Limiting on Public Endpoint:**
- **Finding:** `/api/v1/public/inquiries` has no rate limiting or abuse protection.
- **Risk:** Susceptible to spam, bot submissions, and DoS attacks.
- **Evidence:** No rate limiting dependency or configuration found.
- **Impact:** Public inbox could be flooded with malicious submissions.
- **Required:** Rate limiting (e.g., bucket4j) on public inquiry endpoint.

**4. No Input Sanitization Beyond Validation:**
- **Finding:** PublicInquiryRequest uses Jakarta validation but no additional sanitization.
- **Risk:** XSS or injection attacks through form fields.
- **Evidence:** Validation only, no HTML escaping or content security policy.
- **Impact:** Malicious content could be stored in database.
- **Required:** Input sanitization library or CSP headers.

### Medium (P2)

**5. Error Responses May Reveal Implementation Details:**
- **Finding:** GlobalExceptionHandler returns detailed field errors and exception messages.
- **Risk:** Error responses could expose internal structure or stack traces.
- **Evidence:** GlobalExceptionHandler returns fieldErrors map and exception messages.
- **Impact:** Information leakage to attackers.
- **Required:** Sanitize error messages in production profile.

**6. No Request Tracing:**
- **Finding:** No correlation ID header generation for distributed tracing.
- **Risk:** Difficult to debug production issues and trace request flow.
- **Evidence:** ApiClient checks for `x-correlation-id` header but backend doesn't generate it.
- **Impact:** Reduced observability in production.
- **Required:** Add servlet filter for correlation ID generation.

### Low (P3)

**7. No API Versioning Strategy:**
- **Finding:** API uses `/api/v1` prefix but no explicit versioning strategy or deprecation policy.
- **Risk:** Breaking changes may affect frontend without coordination.
- **Evidence:** Hardcoded `/api/v1` in controllers and client.
- **Impact:** Future evolution may be difficult.
- **Required:** Document versioning strategy (acceptable for P0/P1 to complete first).

---

## Test Coverage and Execution Results

### Backend Test Execution

**Command:** `./mvnw.cmd test`  
**Location:** `C:\Users\Hi\OneDrive\Desktop\growthprint-backend\growthprint-backend`

**Results:**
- **Tests run:** 32
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 11

**Test Breakdown:**
- ClientControllerTest: 3/3 passed ✅
- DataSourceConfigTest: 9/9 passed ✅
- CrmLeadRepositoryIntegrationTest: 0/3 passed (3 skipped - requires Docker) ⚠️
- CrmLeadServiceTest: 6/6 passed ✅
- FlywayMigrationTest: 0/7 passed (7 skipped - requires Docker) ⚠️
- PublicInquiryRequestValidationTest: 2/2 passed ✅
- LeadServiceTest: 1/1 passed ✅
- PostgreSQLIntegrationTest: 0/1 passed (1 skipped - requires Docker) ⚠️

**Skipped Tests Reason:**
- `@Disabled("Requires Docker...")` annotations on database-dependent tests
- Docker unavailable in test environment (`docker: command not found`)

**Test Types:**
- **Unit Tests (21 passing):** ClientControllerTest, DataSourceConfigTest, CrmLeadServiceTest, PublicInquiryRequestValidationTest, LeadServiceTest
- **Integration Tests (11 skipped):** CrmLeadRepositoryIntegrationTest, FlywayMigrationTest, PostgreSQLIntegrationTest

### Frontend Test Execution

**Command:** `npm run test`  
**Location:** `C:\Users\Hi\OneDrive\Desktop\GrowthPrint-frontend`

**Results:**
- **Tests run:** 28
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0

**Test Breakdown:**
- API client initialization and configuration: 3 tests
- API error handling: 2 tests
- Domain rules (calculations, validation): 15 tests
- Utility functions: 6 tests
- Redux state stability: 1 test
- Duration: 1291ms

**Lint Check:**
- **Command:** `npm run lint` (tsc --noEmit)
- **Result:** Passed ✅

### Environmental Blockers

**Docker Unavailability:**
- **Impact:** Cannot run PostgreSQL integration tests
- **Blocker:** Cannot verify migrations, repository operations, foreign keys, constraints against disposable database
- **Workaround:** Would require Docker installation or alternative local PostgreSQL instance

**Admin Endpoints Disabled:**
- **Impact:** Cannot test CRM Leads and raw inquiry endpoints in integration
- **Blocker:** Cannot verify admin endpoints work end-to-end without enabling feature flag
- **Workaround:** Enable `app.admin-endpoints.enabled=true` locally for testing (requires auth implementation first for safety)

---

## Dependency-Aware Implementation Roadmap

### Phase A — Shared Backend Infrastructure and API Conventions

**Goal:** Establish foundation for secure, production-ready API development

**Modules:**
- Spring Security with JWT authentication
- CORS configuration
- Request correlation ID tracing
- Global error response standardization
- OpenAPI documentation completeness
- Rate limiting for public endpoints

**Files Likely Changed:**
- `pom.xml` - Add spring-boot-starter-security, JWT library, rate limiting library
- `src/main/resources/application.yml` - Security, CORS, rate limiting configuration
- `src/main/java/com/growthprint/backend/config/` - SecurityConfig, CorsConfig, TracingFilter
- `src/main/java/com/growthprint/backend/common/exception/GlobalExceptionHandler.java` - Sanitize production errors
- New package: `src/main/java/com/growthprint/backend/security/`
- New package: `src/main/java/com/growthprint/backend/auth/`

**Database Migrations Required:**
- V5__create_users.sql - Users table for authentication

**Prerequisite Work:**
- Define user roles (ADMIN, CLIENT, GUEST)
- Define JWT secret and expiration strategy
- Define CORS allowed origins (frontend URL)

**Security Requirements:**
- JWT-based stateless authentication
- Role-based authorization (@PreAuthorize)
- Public endpoints explicitly annotated
- Admin endpoints require ADMIN role

**Unit Tests:**
- Security configuration tests
- JWT token generation and validation tests
- CORS filter tests
- Rate limiting filter tests

**Integration Tests:**
- Protected endpoint access with valid/invalid tokens
- Role-based authorization tests
- Rate limiting behavior tests

**API Contract Checks:**
- Standardized error response format
- Correlation ID presence in responses

**Acceptance Criteria:**
- JWT authentication works for login/logout
- Protected endpoints reject unauthenticated requests
- Admin endpoints reject non-ADMIN roles
- CORS allows frontend origin
- Rate limiting blocks abuse of public endpoint
- All responses include correlation ID
- OpenAPI documents security scheme

**Dependencies on Other Phases:**
- None (this is foundation)

**Risks and Blockers:**
- JWT secret management in production
- OAuth provider integration if needed
- Complexity of role-based authorization across modules

---

### Phase B — Core Resources and Persistence

**Goal:** Implement fundamental business entities that other modules depend on

**Modules:**
- Users (authentication continuation)
- Clients (update operations)
- Projects
- Tasks
- Activity/audit log

**Files Likely Changed:**
- `src/main/java/com/growthprint/backend/client/` - Add update/delete operations
- New package: `src/main/java/com/growthprint/backend/project/`
- New package: `src/main/java/com/growthprint/backend/task/`
- New package: `src/main/java/com/growthprint/backend/activity/`
- `src/main/resources/db/migration/` - V6, V7, V8 migrations

**Database Migrations Required:**
- V6__create_projects.sql - Projects table
- V7__create_project_milestones.sql - Project milestones table
- V8__create_tasks.sql - Tasks table
- V9__create_activities.sql - Activity/audit log table

**Prerequisite Work:**
- Phase A complete (security infrastructure)

**Security Requirements:**
- Client operations require ADMIN or CLIENT role
- Projects require ADMIN or CLIENT role (client-scoped)
- Tasks require ADMIN or CLIENT role (client-scoped)
- Activity log writes require authentication

**Unit Tests:**
- Project service tests (CRUD, milestone management)
- Task service tests (CRUD, status transitions)
- Activity service tests (event logging, retrieval)
- Client service update/delete tests

**Integration Tests:**
- Projects CRUD with repository
- Tasks linked to projects
- Activity logging triggered by operations
- Client update/delete with repository

**API Contract Checks:**
- Project response matches frontend Project interface
- Task response matches frontend Task interface
- Activity response matches frontend ActivityEvent interface
- Client update matches frontend expectations

**Acceptance Criteria:**
- Projects can be created, listed, retrieved, updated
- Project milestones can be created and updated
- Tasks can be created, listed, updated, linked to projects
- Activity log records all mutations with actor, role, timestamp
- Clients can be updated and deleted
- All operations respect authorization

**Dependencies on Other Phases:**
- Phase A (security infrastructure)

**Risks and Blockers:**
- Project-client relationship complexity
- Task-project-client scoping rules
- Activity log performance at scale

---

### Phase C — CRM and Business Workflow APIs

**Goal:** Implement CRM Leads and Support Requests with full workflow

**Modules:**
- CRM Leads (enable and verify existing implementation)
- Support Requests
- Support Estimates

**Files Likely Changed:**
- `src/main/java/com/growthprint/backend/crm/lead/` - Add delete operation
- New package: `src/main/java/com/growthprint/backend/support/`
- `src/main/resources/db/migration/` - V10, V11 migrations

**Database Migrations Required:**
- V10__create_support_requests.sql - Support requests table
- V11__create_support_estimates.sql - Support estimates table

**Prerequisite Work:**
- Phase A (security infrastructure)
- Phase B (clients, activities)

**Security Requirements:**
- CRM Leads require ADMIN role
- Support requests require ADMIN or CLIENT role
- Support estimates require ADMIN role

**Unit Tests:**
- CRM Lead delete operation test
- Support request service tests (CRUD, classification, status transitions)
- Support estimate service tests (creation, approval, rejection)
- Conversion from inquiry to CRM lead test

**Integration Tests:**
- CRM Lead delete with repository
- Support request workflow (create → classify → estimate → approve → ready → complete)
- Support estimate linked to request
- Inquiry to CRM lead conversion with transaction

**API Contract Checks:**
- Support request response matches frontend SupportRequest interface
- Support estimate response matches frontend SupportEstimate interface
- CRM Lead delete operation matches frontend adapter

**Acceptance Criteria:**
- CRM Leads can be deleted
- Support requests can be created, classified, estimated, approved
- Support estimate workflow matches frontend Redux actions
- Inquiry to CRM lead conversion works transactionally
- All operations respect authorization

**Dependencies on Other Phases:**
- Phase A (security infrastructure)
- Phase B (clients, activities)

**Risks and Blockers:**
- Support request state machine complexity
- Estimate approval workflow rules
- Inquiry conversion transaction safety

---

### Phase D — Operational Modules

**Goal:** Implement proposals, invoices, outreach, and automations

**Modules:**
- Proposals
- Invoices and invoice line items
- Outreach drafts
- Automations/workflows

**Files Likely Changed:**
- New package: `src/main/java/com/growthprint/backend/proposal/`
- New package: `src/main/java/com/growthprint/backend/invoice/`
- New package: `src/main/java/com/growthprint/backend/outreach/`
- New package: `src/main/java/com/growthprint/backend/automation/`
- `src/main/resources/db/migration/` - V12, V13, V14, V15 migrations

**Database Migrations Required:**
- V12__create_proposals.sql - Proposals table
- V13__create_invoices.sql - Invoices table
- V14__create_invoice_line_items.sql - Invoice line items table
- V15__create_outreach_drafts.sql - Outreach drafts table
- V16__create_workflows.sql - Workflows table

**Prerequisite Work:**
- Phase A (security infrastructure)
- Phase B (clients, activities)
- Phase C (CRM leads)

**Security Requirements:**
- Proposals require ADMIN or CLIENT role
- Invoices require ADMIN or CLIENT role
- Outreach drafts require ADMIN role
- Automations require ADMIN role

**Unit Tests:**
- Proposal service tests (CRUD, acceptance workflow)
- Invoice service tests (CRUD, payment, line items)
- Outreach service tests (draft creation, approval, sending)
- Automation service tests (workflow definition, execution, status)

**Integration Tests:**
- Proposal linked to client
- Invoice linked to client with line items
- Outreach draft linked to CRM lead
- Automation execution logging

**API Contract Checks:**
- Proposal response matches frontend Proposal interface
- Invoice response matches frontend Invoice interface
- Outreach draft response matches frontend OutreachDraft interface
- Workflow response matches frontend WorkflowDefinition interface

**Acceptance Criteria:**
- Proposals can be created, accepted, declined
- Invoices can be created, marked paid, with line items
- Outreach drafts can be created, approved, sent
- Automations can be defined, triggered, logged
- All operations respect authorization

**Dependencies on Other Phases:**
- Phase A (security infrastructure)
- Phase B (clients, activities)
- Phase C (CRM leads)

**Risks and Blockers:**
- Invoice calculation logic accuracy
- Outreach email sending (external service integration)
- Automation execution engine complexity (n8n integration mentioned in frontend)

---

### Phase E — Dashboard Aggregates and Notifications

**Goal:** Implement dashboard metrics, notifications, and derived data

**Modules:**
- Dashboard metrics endpoint
- Notifications
- Health/check endpoints

**Files Likely Changed:**
- New package: `src/main/java/com/growthprint/backend/dashboard/`
- New package: `src/main/java/com/growthprint/backend/notification/`
- `src/main/java/com/growthprint/backend/config/` - Health check enhancements
- `src/main/resources/db/migration/` - V17 migration (notifications table if needed)

**Database Migrations Required:**
- V17__create_notifications.sql - Notifications table (if persistent notifications needed)

**Prerequisite Work:**
- All previous phases (data foundations complete)

**Security Requirements:**
- Dashboard metrics require authenticated user (role-scoped)
- Notifications require authenticated user
- Health endpoints can be public (no sensitive data)

**Unit Tests:**
- Dashboard aggregation logic tests
- Notification generation tests
- Health check component tests

**Integration Tests:**
- Dashboard metrics calculated from real data
- Notification creation triggered by events
- Health check database connectivity

**API Contract Checks:**
- Dashboard metrics match frontend AdminDashboard expectations
- Notification response matches frontend notification interface
- Health response format matches agencyApi.systemHealth expectations

**Acceptance Criteria:**
- Dashboard returns accurate metrics (cash flow, pipeline, MRR)
- Notifications generated for key events
- Health check reports service status
- Metrics respect user role (admin vs client view)

**Dependencies on Other Phases:**
- All previous phases (requires all data sources)

**Risks and Blockers:**
- Dashboard aggregation query performance
- Real-time notification delivery (if WebSocket needed)
- Health check external service dependencies

---

### Phase F — Security Hardening and Comprehensive Integration Verification

**Goal:** Complete security implementation, run full integration tests, finalize OpenAPI

**Modules:**
- Security audit and hardening
- Full integration test suite with Docker
- OpenAPI documentation completeness
- API contract validation

**Files Likely Changed:**
- Security configurations (refinement)
- Test configurations (enable Docker tests)
- OpenAPI annotations (completeness)
- New package: `src/main/java/com/growthprint/backend/config/` - Test configuration

**Database Migrations Required:**
- None (unless security requires schema changes)

**Prerequisite Work:**
- All previous phases complete

**Security Requirements:**
- Security audit completed
- Penetration testing recommendations addressed
- Secrets management strategy defined

**Unit Tests:**
- Security rule tests
- Input sanitization tests
- Output encoding tests

**Integration Tests:**
- Full end-to-end workflows with Docker
- API contract validation against frontend types
- Performance tests for dashboard metrics
- Load tests for public inquiry endpoint

**API Contract Checks:**
- All endpoints documented in OpenAPI
- Frontend types match backend DTOs
- Error responses standardized
- Pagination, sorting, filtering documented

**Acceptance Criteria:**
- All integration tests pass with Docker
- Security audit passed
- OpenAPI documentation complete
- No unauthorized access possible
- API contracts validated

**Dependencies on Other Phases:**
- All previous phases

**Risks and Blockers:**
- Docker availability in CI/CD
- Test data setup complexity
- External service mocking for integration tests

---

## First Implementation Task

**Task:** Implement Spring Security with JWT Authentication and Authorization

**Scope:**
- Add Spring Security dependency
- Create SecurityConfig with JWT authentication filter
- Create JWT utility class for token generation and validation
- Create users table (V5 migration)
- Create UserRepository, UserService, UserController
- Implement login endpoint (POST /api/v1/auth/login)
- Implement logout endpoint (POST /api/v1/auth/logout)
- Implement current user endpoint (GET /api/v1/auth/me)
- Add @PreAuthorize annotations to existing admin endpoints
- Configure CORS for frontend origin
- Add CORS configuration class
- Add correlation ID filter
- Update GlobalExceptionHandler to sanitize production errors
- Add unit tests for security configuration
- Add integration tests for protected endpoints

**Files Likely Changed:**
- `pom.xml` - Add spring-boot-starter-security, jjwt library
- `src/main/resources/application.yml` - Security, JWT, CORS configuration
- `src/main/resources/db/migration/V5__create_users.sql` - New migration
- `src/main/java/com/growthprint/backend/config/SecurityConfig.java` - New file
- `src/main/java/com/growthprint/backend/config/CorsConfig.java` - New file
- `src/main/java/com/growthprint/backend/config/TracingFilter.java` - New file
- `src/main/java/com/growthprint/backend/security/` - New package
- `src/main/java/com/growthprint/backend/auth/` - New package
- `src/main/java/com/growthprint/backend/common/exception/GlobalExceptionHandler.java` - Update
- `src/main/java/com/growthprint/backend/crm/lead/controller/CrmLeadController.java` - Add @PreAuthorize
- `src/main/java/com/growthprint/backend/lead/controller/AdminLeadController.java` - Add @PreAuthorize
- Test files for new packages

**Database Migrations Required:**
- V5__create_users.sql

**Prerequisite Work:**
- None (foundational task)

**Security Requirements:**
- JWT secret must be configurable via environment variable
- JWT expiration configurable
- Password hashing with BCrypt
- Role-based authorization (ADMIN, CLIENT, GUEST)
- Public endpoints explicitly marked (@PermitAll)

**Unit Tests:**
- JWT token generation and validation
- Password hashing verification
- Security configuration tests
- CORS filter tests

**Integration Tests:**
- Login with valid credentials returns JWT
- Protected endpoint rejects request without token
- Protected endpoint rejects request with invalid token
- Protected endpoint rejects request with expired token
- Admin endpoint rejects non-ADMIN role
- CORS allows frontend origin

**API Contract Checks:**
- Login response includes access token and user profile
- /me endpoint returns current user profile
- Error responses include correlation ID

**Acceptance Criteria:**
- Login endpoint issues valid JWT token
- Protected endpoints require valid JWT token
- Admin endpoints require ADMIN role
- CORS allows frontend origin
- All responses include correlation ID
- Security tests pass
- Integration tests pass with Docker (if available)

**Dependencies on Other Phases:**
- None (foundational Phase A)

**Risks and Blockers:**
- JWT secret management in production
- Token expiration strategy
- BCrypt strength configuration
- CORS origin configuration for multiple environments

---

## Open Questions and Blockers

**Open Questions:**

1. **Authentication Provider:** Should we implement custom JWT authentication or integrate with OAuth2 provider (e.g., Auth0, Google)? Custom JWT is simpler but requires more security maintenance.

2. **Rate Limiting Strategy:** Should rate limiting be in-memory (single-instance) or distributed (Redis)? Limited production scalability with in-memory rate limiting.

3. **Outreach Email Sending:** Frontend automation mentions n8n integration. Should backend integrate with email service (SendGrid, AWS SES) directly or delegate to n8n? External service integration complexity.

4. **Testcontainers in CI/CD:** Docker unavailable in current test environment. Should we set up CI/CD with Docker runners to enable integration tests, or accept integration testing only in local development?

5. **Database Migration Strategy for Production:** Should we verify V4 migration against a staging database before production deployment? Current verification only against production database is risky.

**Blockers:**

1. **Docker Unavailability:** Cannot run database integration tests (11 tests skipped). Blocks verification of migrations, repository operations, and foreign key constraints.

2. **Authentication Absence:** Cannot enable admin endpoints for testing without compromising security. Blocks end-to-end verification of CRM Leads and raw inquiry endpoints.

3. **No Action Required:** This is an inspection and planning phase only. No implementation, code changes, or database modifications should be made based on this report until explicitly approved.

---

## Conclusion

The GrowthPrint backend has a solid technical foundation with Spring Boot 4.1.1, Java 17, PostgreSQL, and Flyway. Four modules are implemented: public inquiries, raw inquiries, clients, and CRM leads. However, significant gaps exist:

1. **Security:** No authentication/authorization exists; admin endpoints are disabled by feature flag, not by security
2. **Core Business APIs:** Projects, tasks, support requests, proposals, invoices, outreach, automations, and activities have no backend implementation
3. **Test Coverage:** 11 of 32 tests are skipped due to Docker unavailability, preventing database integration verification
4. **API Contract:** Frontend has extensive business logic operating entirely on mock data; backend lacks corresponding persistence

The implementation roadmap prioritizes security infrastructure first (Phase A), then core resources (Phase B), followed by CRM/workflow modules (Phase C), operational modules (Phase D), and finally aggregates and verification (Phase E). The first task is implementing Spring Security with JWT authentication to unblock safe development of the remaining modules.

**STOP CONDITION:** This is an inspection and planning phase only. No implementation, code changes, database modifications, production deployments, or commits should be made based on this report without explicit approval.
