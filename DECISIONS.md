# GrowthPrint Backend - Technical Decisions

## Overview

This document records significant technical and architectural decisions made during the development of the GrowthPrint backend. Each decision includes context, alternatives considered, decision, and consequences.

## Decision 1: Spring Boot Framework

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need a robust Java-based backend framework
- Require REST API capabilities
- Need database integration (JPA)
- Require security framework integration
- Team has Java expertise

**Alternatives Considered:**
1. Spring Boot - Chosen
2. Quarkus
3. Micronaut
4. Dropwizard

**Decision:**
Use Spring Boot 4.1.1 with Java 17.

**Rationale:**
- Mature ecosystem with extensive documentation
- Strong community support
- Seamless integration with Spring Security, Spring Data JPA
- Built-in support for Flyway migrations
- Actuator for monitoring and health checks
- Team familiarity with Spring ecosystem

**Consequences:**
- Positive: Rapid development with auto-configuration
- Positive: Extensive library ecosystem
- Positive: Built-in production-ready features
- Negative: Slightly higher memory footprint
- Negative: Steeper learning curve for beginners

---

## Decision 2: PostgreSQL Database

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need relational database for structured data
- Require ACID transactions
- Need complex query capabilities
- Hosting on Render (PostgreSQL native support)

**Alternatives Considered:**
1. PostgreSQL - Chosen
2. MySQL
3. MongoDB
4. SQLite

**Decision:**
Use PostgreSQL with Render hosting.

**Rationale:**
- Advanced features (JSONB, full-text search, advanced indexing)
- Strong ACID compliance
- Excellent JSON support for flexible schemas
- Render provides managed PostgreSQL
- Mature and reliable
- Strong SQL standard compliance

**Consequences:**
- Positive: Advanced database features available
- Positive: Strong data integrity
- Positive: JSONB for flexible data when needed
- Negative: Requires SQL expertise
- Negative: Vertical scaling limitations (mitigated by Render)

---

## Decision 3: Flyway for Database Migrations

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need versioned database schema management
- Require reproducible deployments
- Team needs to track schema changes
- Multiple environments (dev, staging, prod)

**Alternatives Considered:**
1. Flyway - Chosen
2. Liquibase
3. Manual SQL scripts
4. Hibernate auto-DDL

**Decision:**
Use Flyway for database migrations.

**Rationale:**
- Simple and intuitive SQL-based migrations
- Versioned migration files (V1, V2, V3...)
- Automatic migration on application startup
- Excellent Spring Boot integration
- Community and tooling support
- Prevents schema drift

**Consequences:**
- Positive: Version-controlled schema changes
- Positive: Reproducible deployments
- Positive: Automatic migration on startup
- Negative: No rollback support (by design)
- Negative: Migration conflicts possible in team development

---

## Decision 4: JWT-Based Authentication

**Date:** 2026-10-27
**Status:** Accepted
**Context:**
- Need stateless authentication for REST API
- Require role-based access control
- Frontend is separate Next.js application
- Need scalable authentication solution

**Alternatives Considered:**
1. JWT (JSON Web Tokens) - Chosen
2. Session-based authentication
3. OAuth 2.0 / OpenID Connect
4. API keys

**Decision:**
Use JWT-based authentication with Spring Security.

**Rationale:**
- Stateless design (scalable)
- No server-side session storage required
- Easy integration with frontend
- Standard industry practice
- Spring Security has excellent JWT support
- Supports role-based access control

**Consequences:**
- Positive: Stateless and scalable
- Positive: No session storage overhead
- Positive: Easy frontend integration
- Negative: Token revocation challenging
- Negative: Token payload size limits
- Negative: Requires secure secret management

---

## Decision 5: Layered Architecture

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need clear separation of concerns
- Require maintainable codebase
- Team needs to understand code organization
- Standard Spring Boot pattern

**Alternatives Considered:**
1. Layered Architecture (Controller → Service → Repository) - Chosen
2. Hexagonal Architecture
3. Onion Architecture
4. Clean Architecture

**Decision:**
Use traditional layered architecture.

**Rationale:**
- Standard Spring Boot pattern
- Easy to understand and implement
- Good separation of concerns
- Team familiarity
- Adequate for current project size
- Sufficient for future growth

**Consequences:**
- Positive: Clear separation of concerns
- Positive: Easy to understand
- Positive: Standard Spring Boot pattern
- Negative: Less strict than hexagonal/onion
- Negative: May require refactoring for very large projects

---

## Decision 6: DTO Pattern for API Boundaries

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need to separate internal entities from API contracts
- Require control over API response format
- Need to hide internal implementation details
- Prevent over-fetching and under-fetching

**Alternatives Considered:**
1. DTO Pattern - Chosen
2. Expose JPA entities directly
3. Use serialization annotations on entities
4. Use projection interfaces

**Decision:**
Use DTO pattern for all API boundaries.

**Rationale:**
- Decouples API from internal implementation
- Allows flexible API contracts
- Prevents JPA entity exposure
- Enables validation at API boundary
- Can hide sensitive fields
- Supports API versioning

**Consequences:**
- Positive: Decoupled API contracts
- Positive: Flexible API design
- Positive: Security (hide sensitive fields)
- Negative: Additional mapping code
- Negative: More classes to maintain
- Negative: Potential for mapping errors

---

## Decision 7: Render for Hosting

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need production hosting for backend
- Require PostgreSQL database
- Prefer PaaS for simplicity
- Budget considerations

**Alternatives Considered:**
1. Render - Chosen
2. Heroku
3. AWS (EC2, RDS)
4. DigitalOcean
5. Vercel (frontend only)

**Decision:**
Use Render for backend and PostgreSQL hosting.

**Rationale:**
- Generous free tier
- Built-in PostgreSQL support
- Simple deployment from Git
- Automatic SSL certificates
- Easy environment variable management
- Good documentation

**Consequences:**
- Positive: Simple deployment
- Positive: Built-in PostgreSQL
- Positive: Automatic SSL
- Negative: Limited control over infrastructure
- Negative: Vendor lock-in
- Negative: Potential cost at scale

---

## Decision 8: Testcontainers for Integration Testing

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need integration tests with real database
- Require reproducible test environment
- Want to avoid external test database dependency
- Docker available in CI/CD (planned)

**Alternatives Considered:**
1. Testcontainers - Chosen
2. Embedded H2 database
3. External test database
4. Mock repositories

**Decision:**
Use Testcontainers for integration testing.

**Rationale:**
- Real PostgreSQL in tests
- Reproducible test environment
- No external database dependency
- Integrates with JUnit 5
- Isolated test execution
- Tests match production database

**Consequences:**
- Positive: Real database in tests
- Positive: Reproducible tests
- Positive: No external dependency
- Negative: Requires Docker
- Negative: Slower than in-memory database
- Negative: Currently blocked by Docker unavailability

---

## Decision 9: SpringDoc OpenAPI for API Documentation

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need API documentation for frontend team
- Require interactive API testing
- Want to keep documentation in sync with code
- Standard industry practice

**Alternatives Considered:**
1. SpringDoc OpenAPI - Chosen
2. Swagger Annotations directly
3. Manual documentation (Markdown)
4. RAML or API Blueprint

**Decision:**
Use SpringDoc OpenAPI 3.1.1.

**Rationale:**
- Automatic documentation from annotations
- Interactive Swagger UI for testing
- OpenAPI 3.0 standard
- Minimal configuration required
- Spring Boot integration
- Easy to generate client SDKs

**Consequences:**
- Positive: Automatic documentation
- Positive: Interactive testing
- Positive: OpenAPI standard
- Negative: Additional annotations required
- Negative: Documentation noise in code
- Negative: Disabled in production for security

---

## Decision 10: Feature Flag for Admin Endpoints

**Date:** 2026-10-21
**Status:** Temporary (to be removed)
**Context:**
- Authentication not yet implemented
- Need to develop admin endpoints
- Want to prevent unauthorized access in development
- Temporary measure until auth complete

**Alternatives Considered:**
1. Feature flag (`app.admin-endpoints.enabled`) - Chosen (temporary)
2. No protection (dangerous)
3. Basic auth (insecure)
4. Implement full authentication first (blocking)

**Decision:**
Use feature flag to disable admin endpoints until authentication complete.

**Rationale:**
- Allows development without authentication
- Prevents accidental exposure in production
- Simple to implement
- Clear indicator of incomplete auth
- Easy to remove when auth complete

**Consequences:**
- Positive: Allows parallel development
- Positive: Safety mechanism
- Negative: Temporary technical debt
- Negative: Requires removal later
- Negative: False sense of security

**Removal Plan:**
- Remove feature flag when authentication complete
- Replace with proper @PreAuthorize annotations
- Test authorization thoroughly
- Update documentation

---

## Decision 11: Maven Build System

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need Java build system
- Spring Boot recommends Maven or Gradle
- Team familiarity with Maven
- Need dependency management

**Alternatives Considered:**
1. Maven - Chosen
2. Gradle

**Decision:**
Use Maven with Maven Wrapper.

**Rationale:**
- Spring Boot starter parent POM
- Standard dependency management
- Maven Wrapper for consistent builds
- Larger ecosystem of plugins
- Simpler for teams familiar with Maven
- XML configuration (opinionated)

**Consequences:**
- Positive: Standard Spring Boot setup
- Positive: Maven Wrapper for consistency
- Positive: Extensive plugin ecosystem
- Negative: Slower than Gradle
- Negative: Verbose XML configuration
- Negative: Less flexible than Gradle

---

## Decision 12: Lombok for Boilerplate Reduction

**Date:** 2026-10-21
**Status:** Not Adopted
**Context:**
- Need to reduce boilerplate code
- Want cleaner entity and DTO classes
- Consider Lombok annotations

**Alternatives Considered:**
1. Lombok - Not adopted
2. Manual code generation
3. IDE code generation
4. Kotlin (data classes)

**Decision:**
Do not use Lombok.

**Rationale:**
- Lombok can cause IDE compatibility issues
- Compile-time annotation processing complexity
- Debugging can be challenging
- Spring Boot generates reasonable defaults
- IntelliJ IDEA code generation sufficient
- Prefer explicit code for clarity

**Consequences:**
- Positive: No Lombok dependency
- Positive: Explicit code
- Positive: Better debugging experience
- Negative: More boilerplate code
- Negative: More manual code generation

---

## Decision 13: Entity Graphs for Eager Fetching

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need to avoid N+1 query problems
- Require efficient loading of related entities
- Want control over fetching strategy

**Alternatives Considered:**
1. Entity Graphs - Chosen
2. JOIN FETCH in JPQL
3. FetchType.EAGER (not recommended)
4. Batch fetching

**Decision:**
Use Entity Graphs for eager fetching when needed.

**Rationale:**
- Declarative fetching strategy
- Reusable across queries
- Type-safe
- Avoids N+1 queries
- Flexible per-query control
- Spring Data JPA support

**Consequences:**
- Positive: Efficient queries
- Positive: Declarative
- Positive: Type-safe
- Negative: Additional configuration
- Negative: Complexity for complex graphs

---

## Decision 14: Centralized Exception Handling

**Date:** 2026-10-21
**Status:** Accepted
**Context:**
- Need consistent error responses
- Require proper HTTP status codes
- Want to avoid repetitive exception handling

**Alternatives Considered:**
1. GlobalExceptionHandler with @RestControllerAdvice - Chosen
2. Per-controller exception handling
3. Spring Boot default error handling
4. Custom error controller

**Decision:**
Use GlobalExceptionHandler with @RestControllerAdvice.

**Rationale:**
- Centralized error handling
- Consistent error responses
- Proper HTTP status codes
- ProblemDetail (RFC 7807) standard
- Single place to maintain error logic
- Easy to add new exception types

**Consequences:**
- Positive: Consistent error responses
- Positive: Centralized logic
- Positive: RFC 7807 standard
- Negative: Global handler complexity
- Negative: Harder to handle controller-specific errors

---

## Decision 15: Invoice PDFs with OpenPDF, generated on demand (D4)

**Date:** 2026-11-01
**Status:** Accepted
**Context:**
- Admins and clients need a professional, downloadable invoice document.
- The backend runs on Render, whose service filesystem is ephemeral (files are lost on every deploy/restart).
- Invoice state changes after issue (payment reports verified, PAID, VOID), so a stored file can go stale.

**Alternatives Considered:**
1. OpenPDF (`com.github.librepdf:openpdf`) - Chosen (approved by the product owner)
2. iText 7+ (AGPL / commercial licence - rejected)
3. Apache PDFBox (low-level drawing API, more layout code)
4. HTML-to-PDF (Flying Saucer / OpenHTMLtoPDF) - extra templating layer
5. Generate once and store in object storage - needs a storage service and invalidation

**Decision:**
- Use **OpenPDF 3.0.5** (latest release on Maven Central as of 2026-11-01; requires Java 21; packages `org.openpdf.*`).
  **Licence: dual LGPL 2.1 / MPL 2.0** - used unmodified as a library dependency, which both licences permit.
- Render PDFs **on demand** in `invoice/pdf` (`InvoicePdfRenderer`, `InvoicePdfService`, `InvoicePdfController`);
  **never store them**. Endpoints: `GET /api/v1/invoices/{id}/pdf` (ADMIN) and
  `GET /api/v1/invoices/my/{id}/pdf` (CLIENT, own client only - other invoices return 404).
  `Content-Disposition: attachment` by default, `?disposition=inline` for preview; `Cache-Control: no-store`.
- The PDF shows the invoice's stored subtotal / tax / total exactly (never recalculated). Amount paid = sum of
  VERIFIED payment reports (PAID invoices show the full total as paid, since an admin may reconcile without a
  report); balance due = total - paid, never negative, 0 for PAID and VOID.
- Seller details and payment instructions come only from `app.company.*` (env `COMPANY_*`); unconfigured blocks
  are omitted. No bank, tax or legal details are hard-coded.

**Rationale:**
- OpenPDF has a high-level table API (repeating header rows, wrapping cells, keep-together) that fits invoices.
- On-demand generation is cheap (~tens of ms), always reflects the current invoice state and needs no storage.

**Consequences:**
- Positive: No storage, no stale documents, works on Render's ephemeral filesystem.
- Positive: Permissive-enough licence for a closed-source SaaS (unlike AGPL iText).
- Negative: Each download costs a render; acceptable at current volume (add caching only if it becomes hot).
- Negative: A re-downloaded PDF reflects the current state, not a frozen copy of what was originally sent.
- Negative: Standard PDF fonts (Helvetica, WinAnsi) cover Western European text and EUR/GBP/USD symbols; client
  names in other scripts would need an embedded Unicode font.

---

## Decision 16: One estimate per support request; re-classifying replaces it

**Date:** 2026-11-01 (confirmed by the founder)
**Status:** Accepted

**Context:** Re-classifying a support request after an estimate existed allowed a second estimate, and the
single-estimate read then failed for that request and for every list that included it.

**Decision:** Migration `V16__add_unique_constraint_support_estimates_request_id.sql` makes
`support_estimates.request_id` UNIQUE. Re-classifying is allowed in any status; it deletes the existing estimate and
resets the request to `ESTIMATE_PENDING` (or `READY_TO_START` for included support).

**Alternative considered:** reject re-classification once an estimate exists (no deletion, no unique index). Not chosen.

**Consequences:**
- Positive: at most one estimate per request, enforced by the database.
- Negative: re-classifying deletes the estimate even if the client already approved it, so its `accepted_at` /
  `accepted_by` record is lost; a COMPLETED request can be re-opened by re-classifying. Admins should treat
  re-classification of approved or completed requests with care.
- Deployment risk: V16 fails if any request already has more than one estimate. Before migrating a database, run
  `SELECT request_id, COUNT(*) FROM support_estimates GROUP BY request_id HAVING COUNT(*) > 1;` and resolve any rows.
- AI/user-management migrations are numbered V17 (users and team access) and V19 (AI settings) as a result.

---

## Future Decisions

### Pending Decision: Caching Strategy

**Context:** Need to improve performance for frequently accessed data

**Alternatives to Consider:**
1. Redis
2. Spring Cache abstraction
3. Database query caching
4. Application-level caching

**Status:** Not decided

### Pending Decision: API Rate Limiting

**Context:** Need to protect against abuse and ensure fair usage

**Alternatives to Consider:**
1. Spring Boot starter for rate limiting
2. Bucket4j
3. Resilience4j
4. Cloudflare (infrastructure level)

**Status:** Not decided

### Pending Decision: Message Queue for Async Operations

**Context:** Need to handle long-running operations asynchronously

**Alternatives to Consider:**
1. RabbitMQ
2. Apache Kafka
3. Redis Streams
4. Spring @Async with thread pool

**Status:** Not decided

## Decision Template

When recording a new decision, use this template:

```markdown
## Decision [Number]: [Title]

**Date:** YYYY-MM-DD
**Status:** [Accepted/Rejected/Deprecated]
**Context:**
- [Why is this decision needed?]
- [What problem are we solving?]

**Alternatives Considered:**
1. [Alternative 1]
2. [Alternative 2]
3. [Alternative 3]

**Decision:**
[Decision statement]

**Rationale:**
- [Why did we choose this option?]
- [What benefits does it provide?]

**Consequences:**
- Positive: [Benefit 1]
- Positive: [Benefit 2]
- Negative: [Drawback 1]
- Negative: [Drawback 2]
```
