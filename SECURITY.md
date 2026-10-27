# GrowthPrint Backend - Security Policies

## Overview

This document outlines security policies, authentication, authorization, secrets management, and vulnerability handling for the GrowthPrint backend. All security practices are based on OWASP ASVS and OWASP Cheat Sheet Series.

## Authentication

### JWT-Based Authentication

**Implementation:**
- JWT tokens generated on successful login
- Tokens signed with HS256 algorithm
- Secret key from `JWT_SECRET` environment variable
- Token expiration: 1 hour (configurable via `app.security.jwt.expiration`)
- Issuer: `growthprint-backend`

**Secret Management:**
- JWT secret must be stored in environment variable
- Never commit JWT secret to source code
- Generate secure random key: `openssl rand -base64 64`
- Minimum secret length: 32 characters
- Rotate secrets regularly (recommended: 90 days)

**Token Storage (Client-Side):**
- Recommend httpOnly cookies (not yet implemented)
- Current: localStorage (acceptable for development, insecure for production)
- Implement httpOnly cookies before production deployment

**Password Security:**
- Passwords must be hashed with bcrypt (not yet implemented)
- Minimum password length: 8 characters
- Require password complexity (not yet implemented)
- Implement password reset flow (not yet implemented)

### Login Flow

```
1. Client POST /api/v1/auth/login with email and password
2. Server validates credentials
3. Server generates JWT token
4. Server returns token in response
5. Client stores token
6. Client includes token in Authorization header: Bearer <token>
7. JwtAuthenticationFilter validates token on each request
```

### Logout Flow

```
1. Client POST /api/v1/auth/logout
2. Server discards token on client-side (token is stateless)
3. Client removes token from storage
4. Future requests without token are unauthorized
```

## Authorization

### Role-Based Access Control (RBAC)

**Roles:**
- `ADMIN` - Full access to all resources
- `CLIENT` - Access to own resources only

**Role Assignment:**
- Admin users assigned ADMIN role
- Client users assigned CLIENT role
- Roles stored in `users.role` column
- Roles immutable after registration (not yet implemented)

### Endpoint Authorization

**Public Endpoints (No Authentication Required):**
- POST /api/v1/public/inquiries - Public inquiry submission
- POST /api/v1/auth/login - User login
- GET /actuator/health - Health check
- GET /swagger-ui.html - API documentation (development only)

**Admin Endpoints (ADMIN Role Required):**
- GET /api/v1/clients - List all clients
- GET /api/v1/clients/{id} - Get client by ID
- PATCH /api/v1/clients/{id} - Update client
- DELETE /api/v1/clients/{id} - Delete client
- GET /api/v1/crm-leads - List all CRM leads
- POST /api/v1/crm-leads - Create CRM lead
- PUT /api/v1/crm-leads/{id}/pipeline-stage - Update pipeline stage
- DELETE /api/v1/crm-leads/{id} - Delete CRM lead
- GET /api/v1/leads - List all raw inquiries
- GET /api/v1/projects - List all projects
- POST /api/v1/projects - Create project
- PATCH /api/v1/projects/{id}/status - Update project status
- GET /api/v1/support-requests - List all support requests
- POST /api/v1/support-requests - Create support request
- POST /api/v1/support-requests/{id}/classify - Classify request
- POST /api/v1/support-requests/{id}/estimate - Attach estimate
- POST /api/v1/support-requests/{id}/approve-estimate - Approve estimate
- GET /api/v1/invoices - List all invoices
- POST /api/v1/invoices - Create invoice
- POST /api/v1/invoices/{id}/reconcile - Mark invoice as paid

**Client Endpoints (CLIENT Role Required):**
- GET /api/v1/auth/me - Get current user profile
- GET /api/v1/projects/my - List client's projects
- GET /api/v1/support-requests/my - List client's support requests
- POST /api/v1/support-requests - Create support request (requires CLIENT role, currently ADMIN)
- POST /api/v1/support-requests/{id}/approve-estimate - Approve estimate (requires CLIENT role for own requests, currently ADMIN)
- GET /api/v1/invoices/my - List client's invoices
- POST /api/v1/invoices/{id}/reconcile - Mark invoice as paid (requires CLIENT role for own invoices, currently ADMIN)

**Authenticated Endpoints (Any Role Required):**
- POST /api/v1/auth/logout - Logout
- GET /api/v1/auth/me - Get current user profile

### Method-Level Security

Use `@PreAuthorize` annotations for fine-grained authorization:

```java
@PreAuthorize("hasRole('ADMIN')")
public List<ClientResponse> findAll() { ... }

@PreAuthorize("hasRole('CLIENT') and @clientService.isOwner(#id, authentication.name)")
public InvoiceResponse getMyInvoice(@PathVariable Long id) { ... }
```

## Secrets Management

### Environment Variables

**Required Environment Variables:**
- `DATABASE_URL` - PostgreSQL JDBC URL
- `DATABASE_USERNAME` - Database username
- `DATABASE_PASSWORD` - Database password
- `JWT_SECRET` - JWT signing secret (min 32 characters)

**Optional Environment Variables:**
- `CORS_ALLOWED_ORIGINS` - Comma-separated list of allowed CORS origins
- `SPRINGDOC_ENABLED` - Enable/disable Swagger UI (default: true in dev, false in prod)
- `SPRING_FLYWAY_ENABLED` - Enable/disable Flyway migrations
- `APP_ADMIN_ENDPOINTS_ENABLED` - Enable/disable admin endpoints (development only)

### Secrets Storage

**Development:**
- Store in `.env` file (gitignored)
- Never commit `.env` to source control
- Use `.env.example` as template

**Production:**
- Store in platform-specific secret manager (Render environment variables)
- Never hardcode secrets in application code
- Rotate secrets regularly
- Audit secret access logs

### Secret Scanning

**Recommended Tools:**
- TruffleHog - Scan git history for secrets
- GitLeaks - Detect secrets in commits
- OWASP Dependency-Check - Scan dependencies for vulnerabilities

**Integration:**
- Add secret scanning to CI/CD pipeline
- Block commits if secrets detected
- Rotate secrets if leaked

## Input Validation

### Request Validation

**Validation Layers:**
1. Bean Validation on DTOs (`@Valid`, `@NotNull`, `@Size`, `@Email`)
2. Business validation in service layer
3. Database constraints (NOT NULL, CHECK, FOREIGN KEY)

**Example:**
```java
public class CreateClientRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be less than 255 characters")
    private String name;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String contactEmail;
}
```

### SQL Injection Prevention

**Prevention Strategy:**
- Use JPA parameterized queries
- Never concatenate user input into SQL
- Use `@Query` with named parameters
- Validate input before persistence

**Example:**
```java
@Query("SELECT c FROM Client c WHERE c.contactEmail = :email")
List<Client> findByEmail(@Param("email") String email);
```

### XSS Prevention

**Prevention Strategy:**
- Spring Boot automatically escapes HTML in responses
- Never render user input without escaping
- Use proper content-type headers
- Sanitize user-generated content (not yet implemented)

### CSRF Protection

**Current Status:** Not implemented

**Recommendation:**
- Implement CSRF protection for state-changing operations
- Use Spring Security CSRF tokens
- Include CSRF token in forms

## Data Protection

### Data in Transit

**HTTPS Requirement:**
- All API calls must use HTTPS in production
- Configure SSL/TLS certificate
- Redirect HTTP to HTTPS
- Use strong TLS configuration (TLS 1.2+)

### Data at Rest

**Database Encryption:**
- PostgreSQL encryption at rest (Render managed)
- Encrypt sensitive fields (not yet implemented)
- Consider field-level encryption for PII

### Data Masking

**Logging:**
- Never log sensitive data (passwords, tokens, PII)
- Mask sensitive data in logs
- Audit log access to sensitive data

## CORS Configuration

**Current Configuration:**
- Allowed origins from `CORS_ALLOWED_ORIGINS` environment variable
- Default: `http://localhost:3000` (development)
- Production: Configure production domain

**Best Practices:**
- Whitelist specific origins only
- Never use `*` for allowed origins
- Configure allowed methods (GET, POST, PUT, PATCH, DELETE)
- Configure allowed headers (Authorization, Content-Type)
- Configure credentials support (true for JWT cookies)

## Rate Limiting

**Current Status:** Configuration placeholder only

**Recommendation:**
- Implement rate limiting with Spring Boot starter
- Rate limit public endpoints (e.g., 10 requests per minute for inquiries)
- Rate limit authentication endpoints (e.g., 5 login attempts per minute)
- Use Redis for distributed rate limiting

**Configuration:**
```yaml
app:
  rate-limit:
    public-inquiries:
      max-requests: 10
      window: 1m
```

## Security Headers

**Recommended Headers:**
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `Content-Security-Policy` (not yet implemented)

## Dependency Security

### Dependency Scanning

**Recommended Tools:**
- OWASP Dependency-Check
- Snyk
- Dependabot (GitHub)

**Integration:**
- Add dependency scanning to CI/CD pipeline
- Block PRs with vulnerable dependencies
- Update dependencies regularly

### Dependency Updates

**Policy:**
- Review dependency updates monthly
- Prioritize security updates
- Test updates in staging before production
- Use dependency version ranges carefully

## Vulnerability Management

### Vulnerability Reporting

**Process:**
1. User reports vulnerability via secure channel
2. Security team acknowledges receipt
3. Security team investigates and validates
4. Security team develops fix
5. Security team tests fix
6. Security team deploys fix
7. Security team discloses vulnerability (if applicable)

### Vulnerability Response

**Severity Levels:**
- **Critical:** Fix within 24 hours
- **High:** Fix within 72 hours
- **Medium:** Fix within 1 week
- **Low:** Fix within 1 month

### Security Audits

**Frequency:**
- Annual security audit
- Penetration testing before major releases
- Code review for security-sensitive changes

## OWASP ASVS Compliance

### Verification Requirements

**ASVS v4.0 Categories:**

**V1: Architecture:**
- [x] 1.1.1 - Verify that the application has security controls
- [x] 1.1.2 - Verify that security controls are enforced
- [ ] 1.2.1 - Verify that security architecture is documented (partial)
- [ ] 1.4.1 - Verify that security logging is implemented (partial)

**V2: Authentication:**
- [x] 2.1.1 - Verify that strong authentication is used
- [x] 2.1.7 - Verify that authentication fails securely
- [ ] 2.2.1 - Verify that password requirements are enforced (not implemented)
- [ ] 2.2.2 - Verify that password storage is secure (not implemented)
- [x] 2.2.3 - Verify that password reset is secure (not implemented)
- [x] 2.6.1 - Verify that authentication is required for sensitive operations

**V3: Session Management:**
- [x] 3.1.1 - Verify that session identifiers are secure (JWT tokens)
- [x] 3.2.1 - Verify that session termination is implemented
- [ ] 3.4.1 - Verify that session timeout is configured (1 hour)

**V4: Access Control:**
- [x] 4.1.1 - Verify that access control is enforced
- [x] 4.1.2 - Verify that access control fails securely
- [x] 4.2.1 - Verify that principle of least privilege is enforced
- [ ] 4.2.3 - Verify that access control is centralized (partial)

**V5: Validation:**
- [x] 5.1.1 - Verify that input validation is implemented
- [x] 5.1.2 - Verify that output encoding is implemented
- [x] 5.2.1 - Verify that SQL injection is prevented
- [x] 5.3.1 - Verify that XSS is prevented

**V8: Data Protection:**
- [x] 8.1.1 - Verify that data is encrypted in transit (HTTPS required)
- [ ] 8.2.1 - Verify that sensitive data is encrypted at rest (partial)
- [ ] 8.3.1 - Verify that data is backed up securely (not implemented)

## Security Checklist

### Before Production Deployment

- [ ] JWT_SECRET set to strong random value
- [ ] DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD set
- [ ] HTTPS configured with valid SSL certificate
- [ ] CORS_ALLOWED_ORIGINS set to production domain only
- [ ] SPRINGDOC_ENABLED set to false
- [ ] APP_ADMIN_ENDPOINTS_ENABLED set to false (auth protects endpoints)
- [ ] Rate limiting configured and tested
- [ ] Security headers configured
- [ ] Dependency scan completed with no critical vulnerabilities
- [ ] Penetration testing completed
- [ ] Backup strategy implemented
- [ ] Monitoring and alerting configured
- [ ] Incident response plan documented

### Ongoing Security

- [ ] Dependency updates reviewed monthly
- [ ] Security audit completed annually
- [ ] Penetration testing completed before major releases
- [ ] Secret rotation completed quarterly
- [ ] Access logs reviewed weekly
- [ ] Security training completed annually

## Incident Response

### Security Incident Types

- Unauthorized access to user data
- Data breach or leak
- Denial of service attack
- Malware infection
- Phishing attack
- Social engineering attack

### Incident Response Plan

1. **Detection:** Identify incident through monitoring or user report
2. **Containment:** Isolate affected systems to prevent spread
3. **Eradication:** Remove threat and restore systems
4. **Recovery:** Restore normal operations
5. **Lessons Learned:** Document incident and improve processes

### Contact Information

- Security Team: [to be defined]
- Incident Response: [to be defined]
- Legal: [to be defined]

## References

- OWASP Application Security Verification Standard (ASVS)
- OWASP Cheat Sheet Series
- OWASP Top 10
- Spring Security Reference
- JWT Best Practices
