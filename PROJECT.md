# GrowthPrint Backend - Project Overview

## Purpose

GrowthPrint is a business management platform for digital agencies. The backend provides REST APIs for lead management, client relationships, project tracking, support ticketing, invoicing, and operational workflows.

## Target Users

**Primary Users:**
- Agency administrators (manage leads, clients, projects, invoices)
- Agency clients (view projects, proposals, invoices, submit support requests)
- Support staff (handle support requests and estimates)

**Secondary Users:**
- Sales team (CRM lead pipeline management)
- Project managers (project and milestone tracking)
- Finance team (invoicing and payment reconciliation)

## Core Features

### Implemented Features

**Authentication (P0 - Recently Implemented)**
- JWT-based authentication with Spring Security
- User registration and login
- Role-based access control (ADMIN, CLIENT)
- Session management with JWT tokens
- Password security (not yet implemented - placeholder)

**Lead Management (P1)**
- Public inquiry submission (contact form)
- Raw inquiry tracking (leads table)
- CRM lead pipeline (qualified prospects)
- Lead qualification scoring
- Pipeline stage management (NEW, QUALIFIED, PROPOSAL, NEGOTIATION, WON, LOST)
- Inquiry-to-lead conversion

**Client Management (P1)**
- Client profiles and contact information
- Client status tracking (ACTIVE, INACTIVE, PROSPECT)
- Retainer tier management (STARTER, GROWTH, ENTERPRISE)
- Billing currency and contract tracking
- Account manager assignment

**Project Management (P2)**
- Project creation and tracking
- Project status management (PLANNING, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED)
- Project deliverables tracking
- Milestone management

**Support Requests (P2)**
- Support ticket creation and tracking
- Request classification (BUG, FEATURE, QUESTION, OTHER)
- Cost estimation for support requests
- Estimate approval workflow
- Request status management (OPEN, IN_PROGRESS, RESOLVED, CLOSED)

**Invoicing (P2)**
- Invoice creation and tracking
- Invoice line items
- Payment reconciliation
- Invoice status management (DRAFT, SENT, PAID, OVERDUE, CANCELLED)

**Outreach Management (P3)**
- Email draft creation
- Draft approval workflow
- Draft sending automation
- Outreach campaign tracking

### Planned Features

**Task Management (P2)**
- Task creation and assignment
- Task status tracking (TODO, IN_PROGRESS, DONE)
- Task-project association

**Proposals (P3)**
- Proposal creation and tracking
- Proposal status management
- Proposal-client association

**Automation (P3)**
- Workflow automation engine
- Custom workflow triggers
- Automated actions

**Activity Audit (P3)**
- Activity event logging
- Audit trail for all operations
- Activity timeline views

**Messaging (P3)**
- In-app messaging system
- Notification system
- Message-client association

## Requirements

### Functional Requirements

**Authentication**
- Users must authenticate with email and password
- JWT tokens must expire after configurable time (default: 1 hour)
- Admin users can access all admin endpoints
- Client users can only access their own data
- Public endpoints must not require authentication

**Lead Management**
- Public users can submit inquiries without authentication
- Admin users can view and manage all leads
- CRM leads must have qualification scores (0-100)
- Pipeline stages must enforce valid transitions
- Inquiries can be converted to CRM leads

**Client Management**
- Admin users can create, view, and update clients
- Client status must be valid enum value
- Retainer tier must be valid enum value
- Billing currency must be valid enum value
- Clients can be associated with projects and invoices

**Project Management**
- Admin users can create and manage projects
- Projects must be associated with a client
- Project status must be valid enum value
- Projects can have multiple deliverables
- Projects can have multiple milestones

**Support Requests**
- Both admin and client users can create support requests
- Clients can only view their own support requests
- Support requests can have cost estimates
- Estimates must be approved before implementation
- Support requests can be classified by category

**Invoicing**
- Admin users can create and manage invoices
- Invoices must be associated with a client
- Invoices can have multiple line items
- Invoices can be marked as paid
- Clients can view their own invoices

### Non-Functional Requirements

**Performance**
- API response time < 200ms for simple queries
- API response time < 500ms for complex queries
- Database queries must use indexes appropriately
- Pagination for list endpoints (not yet implemented)

**Security**
- All API endpoints must use HTTPS in production
- JWT secrets must be stored in environment variables
- Input validation on all request DTOs
- SQL injection prevention via JPA parameterized queries
- XSS prevention via proper response encoding
- CORS configured for allowed origins only

**Reliability**
- Database transactions for write operations
- Flyway migrations for schema changes
- Graceful error handling with proper HTTP status codes
- Database connection pooling configured

**Scalability**
- Stateless REST API design
- Database connection pooling
- Horizontal deployment capability (not yet tested)
- Caching strategy (not yet implemented)

**Maintainability**
- Layered architecture (Controller → Service → Repository)
- DTOs for API boundaries
- Comprehensive unit and integration tests
- API documentation via Swagger/OpenAPI
- Code follows Spring Boot best practices

## Project Scope

### In Scope

**Backend API Development**
- REST API endpoints for all features
- Database schema via Flyway migrations
- Business logic in service layer
- Authentication and authorization
- Input validation and error handling
- API documentation

**Data Persistence**
- PostgreSQL database schema
- Entity-relationship modeling
- Data integrity constraints
- Migration management

**Testing**
- Unit tests for business logic
- Integration tests for repository operations
- API tests for endpoints
- Migration tests for schema changes

### Out of Scope

**Frontend Development**
- Frontend is separate Next.js repository
- API contracts defined in API_COVERAGE_MATRIX.md
- Mock data for frontend development

**Infrastructure**
- CI/CD pipeline configuration (not yet implemented)
- Container orchestration (not yet implemented)
- Monitoring and alerting (not yet implemented)
- Performance monitoring (not yet implemented)

**External Integrations**
- Email service integration (not yet implemented)
- Payment gateway integration (not yet implemented)
- Third-party CRM integration (not yet implemented)
- File storage service (not yet implemented)

## Constraints

**Technical Constraints**
- Java 17 required
- Spring Boot 4.1.1 framework
- PostgreSQL database (Render hosting)
- Maven build system
- Docker unavailable in test environment (limits integration testing)

**Business Constraints**
- Admin endpoints disabled until authentication fully integrated
- Mock mode in frontend may mask API contract issues
- No current production deployment
- No staging environment

**Security Constraints**
- JWT secret must be kept secret
- Database credentials must not be committed
- API must use HTTPS in production
- CORS must be configured for allowed origins

## Success Criteria

**Technical Success**
- All unit tests pass (21 tests)
- Integration tests pass when Docker available (11 tests)
- Code compiles without errors
- No security vulnerabilities in dependencies
- API documentation complete and accurate

**Business Success**
- All planned features implemented
- API contracts match frontend requirements
- Performance requirements met
- Authentication and authorization secure
- Data integrity maintained

**User Success**
- Admin users can manage leads, clients, projects
- Client users can view their data
- Support requests flow works end-to-end
- Invoicing workflow complete
- System is reliable and performant

## Dependencies

**Internal Dependencies**
- Frontend Next.js application (API consumer)
- PostgreSQL database (Render hosting)
- JWT authentication library (jjwt 0.12.6)

**External Dependencies**
- Spring Boot framework and starters
- PostgreSQL JDBC driver
- Flyway migration tool
- Testcontainers for integration testing
- SpringDoc OpenAPI for documentation

## Risks

**Technical Risks**
- Docker unavailable in test environment limits integration testing
- No CI/CD pipeline for automated testing
- No staging environment for pre-production testing
- Database migration errors could block deployment

**Security Risks**
- JWT secret exposure could compromise authentication
- Admin endpoints currently protected by feature flag only
- No rate limiting implemented (except configuration placeholder)
- No input sanitization beyond Bean Validation

**Business Risks**
- API contract mismatches with frontend
- Mock mode in frontend may hide integration issues
- Scope creep with additional features
- Performance issues at scale (not tested)

## Mitigation Strategies

**Technical Mitigations**
- Enable Docker in CI/CD when possible
- Implement CI/CD pipeline with GitHub Actions
- Set up staging environment
- Test migrations thoroughly before production

**Security Mitigations**
- Store JWT secret in environment variables
- Implement proper authorization before removing feature flag
- Add rate limiting with Spring Boot starter
- Add input sanitization for user-generated content

**Business Mitigations**
- Maintain API_COVERAGE_MATRIX.md for contract tracking
- Test real API integration before disabling mock mode
- Prioritize P0 and P1 features first
- Performance test with realistic data volumes
