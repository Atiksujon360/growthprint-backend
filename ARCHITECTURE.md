# GrowthPrint Backend - Architecture

## System Overview

GrowthPrint backend is a Spring Boot 4.1.1 REST API that provides business management functionality for digital agencies. It follows a layered architecture pattern with clear separation of concerns across controller, service, and repository layers.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (Next.js)                      │
│                    (Separate Repository)                     │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS
                            │ JWT Authentication
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot Application                    │
│                      (Port 8090)                             │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│   Controller  │  │    Service    │  │  Repository  │
│    Layer      │  │    Layer      │  │    Layer      │
└───────┬───────┘  └───────┬───────┘  └───────┬───────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
                           ▼
                  ┌────────────────┐
                  │  PostgreSQL    │
                  │  (Render)      │
                  └────────────────┘
```

## Module Structure

### Package Organization

```
com.growthprint.backend/
├── config/                 # Configuration beans
│   ├── SecurityConfig.java
│   ├── DataSourceConfig.java
│   └── FlywayConfig.java
├── controller/             # REST controllers (thin)
│   ├── auth/
│   │   └── AuthController.java
│   ├── client/
│   │   └── ClientController.java
│   ├── crm/
│   │   └── lead/
│   │       └── CrmLeadController.java
│   ├── inquiry/
│   │   └── PublicInquiryController.java
│   ├── lead/
│   │   └── AdminLeadController.java
│   ├── project/
│   │   └── ProjectController.java
│   ├── support/
│   │   └── SupportRequestController.java
│   └── invoice/
│       └── InvoiceController.java
├── service/                # Business logic
│   ├── auth/
│   │   └── AuthService.java
│   ├── client/
│   │   └── ClientService.java
│   ├── crm/
│   │   └── lead/
│   │       └── CrmLeadService.java
│   ├── inquiry/
│   │   └── LeadService.java
│   ├── project/
│   │   └── ProjectService.java
│   ├── support/
│   │   └── SupportRequestService.java
│   └── invoice/
│       └── InvoiceService.java
├── repository/             # Data access (JPA)
│   ├── auth/
│   │   └── UserRepository.java
│   ├── client/
│   │   └── ClientRepository.java
│   ├── crm/
│   │   └── lead/
│   │       └── CrmLeadRepository.java
│   ├── lead/
│   │   └── LeadRepository.java
│   ├── project/
│   │   └── ProjectRepository.java
│   ├── support/
│   │   └── SupportRequestRepository.java
│   └── invoice/
│       └── InvoiceRepository.java
├── entity/                 # JPA entities
│   ├── auth/
│   │   ├── User.java
│   │   ├── UserRole.java
│   │   └── AccountStatus.java
│   ├── client/
│   │   ├── Client.java
│   │   └── ClientEnums.java
│   ├── crm/
│   │   └── lead/
│   │       ├── CrmLead.java
│   │       └── CrmLeadEnums.java
│   ├── lead/
│   │   └── Lead.java
│   ├── project/
│   │   ├── Project.java
│   │   └── ProjectEnums.java
│   ├── support/
│   │   ├── SupportRequest.java
│   │   └── SupportRequestEnums.java
│   └── invoice/
│       ├── Invoice.java
│       └── InvoiceEnums.java
├── dto/                    # Request/response DTOs
│   ├── auth/
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   └── UserProfileResponse.java
│   ├── client/
│   │   └── ClientResponse.java
│   ├── crm/
│   │   └── lead/
│   │       ├── CrmLeadResponse.java
│   │       └── CreateCrmLeadCommand.java
│   ├── inquiry/
│   │   ├── PublicInquiryRequest.java
│   │   └── InquiryAcceptedResponse.java
│   └── common/
│       └── ApiErrorResponse.java
├── exception/              # Custom exceptions
│   ├── AuthenticationFailedException.java
│   ├── UserNotFoundException.java
│   ├── ClientNotFoundException.java
│   └── GlobalExceptionHandler.java
└── common/                 # Shared utilities
    └── dto/
        └── ApiErrorResponse.java
```

## Layer Responsibilities

### Controller Layer

**Responsibilities:**
- Handle HTTP requests and responses
- Validate request DTOs with `@Valid`
- Delegate business logic to service layer
- Map service responses to DTOs
- Handle HTTP-specific concerns (status codes, headers)
- Thin layer: No business logic

**Example:**
```java
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {
    private final ClientService clientService;

    @GetMapping
    public List<ClientResponse> list() {
        return clientService.findAll();
    }

    @GetMapping("/{id}")
    public ClientResponse getById(@PathVariable Long id) {
        return clientService.findById(id);
    }
}
```

### Service Layer

**Responsibilities:**
- Contain business logic
- Manage transactions with `@Transactional`
- Coordinate between multiple repositories
- Apply business rules and validations
- Map entities to DTOs
- Publish domain events (future)

**Example:**
```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;

    public List<ClientResponse> findAll() {
        return clientRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ClientResponse create(CreateClientRequest request) {
        Client client = toEntity(request);
        client = clientRepository.save(client);
        return toResponse(client);
    }
}
```

### Repository Layer

**Responsibilities:**
- Handle JPA persistence
- Define custom queries with `@Query`
- Use entity graphs for eager fetching
- Encapsulate database-specific logic
- No business logic

**Example:**
```java
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    @Query("SELECT c FROM Client c WHERE c.status = :status")
    List<Client> findByStatus(@Param("status") ClientStatus status);

    @EntityGraph(attributePaths = {"projects"})
    Optional<Client> findWithProjectsById(Long id);
}
```

## Data Flow

### Request Flow

```
1. HTTP Request → Controller
2. Controller validates request DTO
3. Controller calls Service method
4. Service begins transaction (if write operation)
5. Service calls Repository method
6. Repository executes JPA query
7. Repository returns Entity/Entities
8. Service maps Entity to DTO
9. Service commits transaction
10. Service returns DTO to Controller
11. Controller returns DTO with HTTP status
12. HTTP Response to Client
```

### Authentication Flow

```
1. Client sends login request with credentials
2. AuthController validates credentials
3. AuthService generates JWT token
4. AuthController returns token in response
5. Client stores token and includes in Authorization header
6. JwtAuthenticationFilter validates token on each request
7. SecurityConfig checks user roles for authorization
8. Request proceeds to Controller if authorized
```

## Module Boundaries

### Auth Module

**Purpose:** User authentication and authorization

**Entities:** User, UserRole, AccountStatus

**Endpoints:**
- POST /api/v1/auth/login
- POST /api/v1/auth/logout
- GET /api/v1/auth/me

**Dependencies:** UserRepository, JWT library

**Exposed to:** All modules (via SecurityConfig)

### Client Module

**Purpose:** Client relationship management

**Entities:** Client, ClientStatus, RetainerTier, BillingCurrency

**Endpoints:**
- GET /api/v1/clients
- GET /api/v1/clients/{id}
- PATCH /api/v1/clients/{id} (planned)
- DELETE /api/v1/clients/{id} (planned)

**Dependencies:** ClientRepository

**Exposed to:** Project, Invoice, Support modules

### CRM Lead Module

**Purpose:** Qualified sales prospect management

**Entities:** CrmLead, PipelineStage, Source, TargetService

**Endpoints:**
- GET /api/v1/crm-leads
- GET /api/v1/crm-leads/{id}
- POST /api/v1/crm-leads
- PUT /api/v1/crm-leads/{id}/pipeline-stage
- POST /api/v1/crm-leads/inquiry/{inquiryId}/convert
- DELETE /api/v1/crm-leads/{id} (planned)

**Dependencies:** CrmLeadRepository, LeadRepository

**Exposed to:** Outreach module

### Project Module

**Purpose:** Project and milestone tracking

**Entities:** Project, ProjectStatus, ProjectMilestone

**Endpoints:**
- GET /api/v1/projects
- GET /api/v1/projects/{id}
- POST /api/v1/projects
- PATCH /api/v1/projects/{id}/status
- POST /api/v1/projects/{projectId}/milestones

**Dependencies:** ProjectRepository, ClientRepository

**Exposed to:** Task, Invoice modules

### Support Request Module

**Purpose:** Support ticketing and estimation

**Entities:** SupportRequest, SupportStatus, RequestCategory, SupportEstimate

**Endpoints:**
- GET /api/v1/support-requests
- GET /api/v1/support-requests/{id}
- GET /api/v1/support-requests/my (client scope)
- POST /api/v1/support-requests
- POST /api/v1/support-requests/{id}/classify
- POST /api/v1/support-requests/{id}/estimate
- POST /api/v1/support-requests/{id}/approve-estimate
- PATCH /api/v1/support-requests/{id}/status

**Dependencies:** SupportRequestRepository, ClientRepository

**Exposed to:** Invoice module

### Invoice Module

**Purpose:** Invoicing and payment reconciliation

**Entities:** Invoice, InvoiceStatus, InvoiceLineItem

**Endpoints:**
- GET /api/v1/invoices
- GET /api/v1/invoices/{id}
- GET /api/v1/invoices/my (client scope)
- POST /api/v1/invoices
- POST /api/v1/invoices/{id}/reconcile

**Dependencies:** InvoiceRepository, ClientRepository

**Exposed to:** None (leaf module)

## Database Schema

### Current Tables (V12)

- `users` - User accounts and authentication
- `leads` - Raw public inquiries
- `clients` - Client profiles
- `crm_leads` - Qualified sales prospects
- `projects` - Project tracking
- `project_deliverables` - Project deliverables
- `tasks` - Task management
- `proposals` - Sales proposals
- `support_requests` - Support tickets
- `support_estimates` - Cost estimates
- `invoices` - Billing invoices
- `invoice_line_items` - Invoice line items
- `outreach_drafts` - Outreach email drafts
- `activities` - Activity audit log
- `notifications` - User notifications

### Key Relationships

- `crm_leads.inquiry_id` → `leads.id` (optional, ON DELETE SET NULL)
- `projects.client_id` → `clients.id` (required)
- `tasks.project_id` → `projects.id` (required)
- `support_requests.client_id` → `clients.id` (required)
- `support_requests.project_id` → `projects.id` (optional)
- `support_estimates.support_request_id` → `support_requests.id` (one-to-one)
- `invoices.client_id` → `clients.id` (required)
- `proposals.client_id` → `clients.id` (required)
- `outreach_drafts.lead_id` → `crm_leads.id` (required)
- `users.client_id` → `clients.id` (optional, for client users)

## Security Architecture

### Authentication

- JWT-based stateless authentication
- JWT filter validates tokens on each request
- Tokens signed with secret from `JWT_SECRET` environment variable
- Token expiration: 1 hour (configurable)

### Authorization

- Role-based access control (RBAC)
- Roles: ADMIN, CLIENT
- Method-level security with `@PreAuthorize`
- Endpoint-level security in SecurityConfig

### Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    // JWT filter configuration
    // Role-based endpoint permissions
    // CORS configuration
    // Public endpoint whitelist
}
```

## API Documentation

- SpringDoc OpenAPI 3.1.1
- Swagger UI at http://localhost:8090/swagger-ui.html
- OpenAPI JSON at http://localhost:8090/v3/api-docs
- Controller-level tags for organization
- Operation descriptions and examples

## External Integrations

### Current Integrations

- PostgreSQL database (Render hosting)
- JWT library (jjwt 0.12.6)

### Planned Integrations

- Redis (caching)
- Email service (outreach)
- Payment gateway (invoicing)
- File storage (attachments)

## Scaling Considerations

### Horizontal Scaling

- Stateless REST API design
- JWT tokens stored client-side
- No session state in application
- Database connection pooling

### Vertical Scaling

- Database read replicas (not implemented)
- Caching layer (not implemented)
- Query optimization (not implemented)

### Performance

- Entity graphs for eager fetching
- Database indexes on foreign keys
- Pagination for list endpoints (not implemented)
- Response compression (not implemented)

## Monitoring and Observability

### Current Monitoring

- Spring Boot Actuator health endpoint
- Health check at /actuator/health
- Info endpoint at /actuator/info

### Planned Monitoring

- Metrics collection (Micrometer)
- Distributed tracing (not implemented)
- Log aggregation (not implemented)
- APM integration (not implemented)

## Deployment Architecture

### Current Deployment

- Render hosting (not yet deployed)
- PostgreSQL 18.6 on Render
- Environment variables via Render dashboard

### Planned Deployment

- Docker containerization
- Kubernetes orchestration (optional)
- CI/CD pipeline (GitHub Actions)
- Blue-green deployment (optional)

## Architecture Principles

1. **Layered Architecture:** Clear separation between controller, service, and repository layers
2. **DTO Pattern:** Never expose JPA entities directly; use DTOs for API boundaries
3. **Transaction Management:** Transactions at service layer with correct read/write scoping
4. **Lazy Loading:** Default JPA relationships to LAZY; use entity graphs when needed
5. **Validation:** Bean Validation on DTOs; business validation in service layer
6. **Exception Handling:** Global exception handler maps exceptions to HTTP responses
7. **Security First:** Authentication and authorization enforced at all layers
8. **Database-First:** Schema changes via Flyway migrations only
9. **API-First:** API contracts documented with OpenAPI
10. **Test-Driven:** Unit and integration tests for all business logic
