# GrowthPrint Backend - AI Agent Guidelines

Shared rules for Devin Desktop, Codex, and other AI coding agents working on this Spring Boot backend.

## 🚨 GLOBAL RULE - READ FIRST BEFORE ANY ACTION

**CRITICAL:** Before implementing, changing, or doing ANYTHING in this project, Devin MUST read the following governance files in this order to understand context:

1. **AGENTS.md** (this file) - AI coding rules, safe editing, tool handoffs
2. **PROJECT.md** - Project purpose, features, users, scope, requirements
3. **PROJECT_STATUS.md** - Current progress, blockers, uncommitted changes, next tasks
4. **ARCHITECTURE.md** - System architecture, modules, boundaries, data flow
5. **SECURITY.md** - Security policies, authentication, authorization, secrets
6. **QUALITY_GATES.md** - Required tests, builds, security scans, release criteria
7. **DEVELOPMENT.md** - Setup instructions, verified commands, troubleshooting
8. **DECISIONS.md** - Technical decisions and rationale

**Why this is required:**
- Prevents unintended breaking changes
- Ensures alignment with project goals and architecture
- Avoids duplicating work or reversing decisions
- Identifies current blockers and uncommitted changes
- Respects established security and quality standards
- Provides context for informed decision-making

**Exception:** If you are only reading or inspecting files (not making changes), you may skip this rule. But before ANY edit, creation, deletion, or code change, you MUST read these files first.

## Repository Context

**Technology Stack:**
- Spring Boot 4.1.1, Java 17
- PostgreSQL with Flyway migrations
- Spring Security with JWT authentication
- Maven build system
- Testcontainers for integration testing
- SpringDoc OpenAPI for API documentation

**Project State:**
- Authentication module implemented (JWT with Spring Security)
- Core modules: public inquiries, clients, CRM leads, projects, support requests, invoices, outreach
- Admin endpoints protected by feature flag (`app.admin-endpoints.enabled=false`) until auth fully integrated
- Docker unavailable in test environment (11/32 integration tests skipped)
- Multiple uncommitted changes pending

## Safe Editing Rules

### Before Making Changes

1. **Read existing files first** - Always read files before editing to understand context and patterns
2. **Check Git status** - Run `git status` to see uncommitted changes; preserve user work
3. **Verify dependencies** - Check `pom.xml` before adding new dependencies; prefer existing libraries
4. **Review existing tests** - Understand test patterns before writing new tests
5. **Check configuration** - Review `application.yml` and `application-dev.yml` before modifying config

### Code Changes

1. **Follow layered architecture** - Controller → Service → Repository pattern (see `.devin/skills/springboot-patterns/SKILL.md`)
2. **Use DTOs for API boundaries** - Never expose JPA entities directly; use request/response DTOs
3. **Validate inputs** - Add `@Valid` on controller methods and validation annotations on DTOs
4. **Add transactions correctly** - Use `@Transactional(readOnly = true)` on read services, `@Transactional` on write services
5. **Handle exceptions properly** - Use specific exceptions; let `GlobalExceptionHandler` map to HTTP responses
6. **Use FetchType.LAZY** - Default for JPA relationships; use `@EntityGraph` or `JOIN FETCH` when needed
7. **Add unit tests** - Write tests for new business logic in service layer
8. **Add integration tests** - Write repository tests with Testcontainers (will run when Docker available)

### Database Changes

1. **Always use Flyway migrations** - Never modify schema manually; create new migration file in `src/main/resources/db/migration/`
2. **Version migrations sequentially** - Next version after V12 is V13, format: `V13__description.sql`
3. **Add constraints in migrations** - Use CHECK constraints for enums, NOT NULL for required fields, foreign keys for relationships
4. **Add indexes for query performance** - Index foreign keys and frequently queried columns
5. **Test migrations locally** - Apply migrations to local PostgreSQL before committing
6. **Never modify existing migrations** - If mistake, create new migration to fix
7. **Update test expectations** - If migration adds table, update `FlywayMigrationTest` to verify

### Security Rules

1. **Never commit secrets** - Do not add credentials, JWT secrets, or API keys to code
2. **Check JWT_SECRET handling** - Ensure JWT secret loaded from environment variable, not hardcoded
3. **Review authorization** - Check `@PreAuthorize` annotations match intended role-based access
4. **Validate input thoroughly** - Use Bean Validation on all request DTOs
5. **Sanitize error messages** - Don't expose stack traces or internal details in API responses
6. **Enable CORS carefully** - Check `CORS_ALLOWED_ORIGINS` config before adding origins
7. **Never disable security for convenience** - Admin endpoints use feature flag temporarily; replace with real auth

## Tool Handoffs

### Devin Desktop → Codex (Review/Alternative Implementation)

**Handoff Context to Include:**
- Current task and specific file paths being modified
- Existing test coverage and test results
- Configuration context (application.yml, .env variables)
- Database schema state (latest migration version)
- Authentication status (JWT configured, admin endpoints feature flag)
- Uncommitted Git changes and rationale

**When to Use Codex:**
- Alternative implementation review for complex business logic
- Second opinion on architecture decisions
- Code review for security-sensitive changes
- Refactoring suggestions for performance

### Devin Desktop → IntelliJ IDEA (Testing/Debugging)

**When to Use IntelliJ:**
- Running Spring Boot application with debugger
- Manually testing API endpoints with HTTP client
- Step-through debugging of complex service logic
- Database query inspection with database tools
- Running specific tests with IDE test runner

**Handoff Context:**
- Main class: `GrowthprintBackendApplication`
- Server port: 8090 (configured in application.yml)
- Swagger UI: http://localhost:8090/swagger-ui.html
- Actuator health: http://localhost:8090/actuator/health
- Required environment variables in .env: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, JWT_SECRET

## Testing Requirements

### Unit Tests

1. **Service layer tests** - Test business logic with mocked repositories
2. **Controller tests** - Test HTTP layer with `@WebMvcTest`, mock service layer
3. **Validation tests** - Test request DTO validation annotations
4. **Exception handling** - Test custom exceptions map to correct HTTP responses
5. **Run before committing** - `.\mvnw.cmd test` must pass (32 tests, 11 skipped without Docker)

### Integration Tests

1. **Repository tests** - Use Testcontainers with PostgreSQL (currently skipped without Docker)
2. **Flyway migration tests** - Verify migrations apply correctly (currently skipped without Docker)
3. **Full context tests** - `@SpringBootTest` with all beans (currently skipped without Docker)
4. **Authentication tests** - Test JWT filter and security configuration
5. **CORS tests** - Verify CORS configuration in integration environment

### Test Pattern

```java
@SpringBootTest
@Testcontainers
class ExampleIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void testOperation() {
        // Test implementation
    }
}
```

## Scope Control

### In Scope

- New REST endpoints following existing controller patterns
- New database tables via Flyway migrations
- Business logic in service layer
- DTOs for request/response
- Unit and integration tests
- Configuration updates in application.yml
- Security configuration in SecurityConfig

### Out of Scope (Requires Authorization)

- Removing or bypassing authentication
- Disabling security features permanently
- Exposing admin endpoints without auth
- Hardcoding credentials or secrets
- Modifying production data directly
- Changes to deployment infrastructure (Render, CI/CD)

### Definition of Done

A task is complete when:
1. Code compiles without errors: `.\mvnw.cmd clean compile`
2. All unit tests pass: `.\mvnw.cmd test` (32 tests, 11 skipped acceptable without Docker)
3. New code has test coverage (unit tests for services, integration tests if Docker available)
4. Database migration created if schema changed
5. API documentation updated (Swagger annotations on controller)
6. No new security vulnerabilities introduced
7. Code follows existing patterns (layered architecture, DTOs, transactions)
8. Git changes are minimal and focused on the task

## Stop Conditions

**Stop and Ask User:**
- Deleting or renaming existing tables (breaking change)
- Removing authentication or authorization
- Modifying JWT implementation
- Changing database migration version (e.g., reverting V10 to V9)
- Adding new external dependencies not in pom.xml
- Modifying production configuration
- Changing feature flags without understanding implications
- Removing or disabling tests without replacement

## Project-Specific Guidelines

### Spring Boot Patterns

Use the Spring Boot patterns skill for reference: `.devin/skills/springboot-patterns/SKILL.md`

Key patterns:
- Controllers delegate to services (thin controllers)
- Services contain business logic and manage transactions
- Repositories handle JPA persistence
- DTOs for API boundaries, never entities
- Global exception handler with ProblemDetail responses
- Entity graphs or JOIN FETCH to avoid N+1 queries

### Database Migrations

- Migration files in `src/main/resources/db/migration/`
- Current version: V12 (add_client_id_to_users.sql)
- Next version: V13
- Pattern: `V{number}__snake_case_description.sql`
- Test with: `mvn test -Dtest=FlywayMigrationTest` (requires Docker)

### Authentication

- JWT-based authentication implemented
- Spring Security configuration in `SecurityConfig.java`
- JWT secret from `JWT_SECRET` environment variable
- User roles: ADMIN, CLIENT
- Admin endpoints protected by feature flag until auth fully integrated
- Never remove the feature flag without implementing proper authorization

### Testing Without Docker

- 11 tests skipped by default (require Docker/Testcontainers)
- Acceptable to skip when Docker unavailable
- Business logic tests (21 tests) must pass
- When Docker becomes available, enable integration tests
- Testcontainers configured for PostgreSQL 16-alpine

## Documentation to Reference

- `README.md` - Project overview and setup
- `API_INVENTORY_AND_IMPLEMENTATION_PLAN.md` - Complete API inventory and gaps
- `POSTGRESQL_INTEGRATION_TEST_STATUS.md` - Database test status
- `AGENTS.md` - This file (shared rules)
- `.devin/skills/springboot-patterns/SKILL.md` - Spring Boot patterns
- Application configuration: `src/main/resources/application.yml`

## Completion Verification

Before marking a task complete:

1. **Verify compilation**: `.\mvnw.cmd clean compile`
2. **Run tests**: `.\mvnw.cmd test` - Ensure 21 unit tests pass, 11 skipped acceptable
3. **Check Git status**: `git status` - Verify only intended files changed
4. **Review changes**: `git diff` - Ensure no secrets, no breaking changes
5. **Verify migration**: If schema changed, confirm migration file created
6. **Check security**: No credentials committed, no auth bypassed
7. **Test locally**: If possible, start app and verify endpoint works
8. **Update docs**: If API changed, ensure documentation reflects changes

## Emergency Procedures

If a test fails:
1. Read the test failure message carefully
2. Check if it's a known skipped test (Docker-related)
3. If new failure, investigate the code change
4. Fix the issue or revert the change
5. Never commit with failing tests

If migration fails:
1. Check SQL syntax in migration file
2. Verify migration version sequence
3. Test migration against local PostgreSQL
4. Do not modify existing migration; create new fix migration if needed

If authentication breaks:
1. Check JWT_SECRET environment variable
2. Verify SecurityConfig changes
3. Review role-based authorization annotations
4. Test with Swagger UI or curl
5. Never disable security to "fix" the issue
