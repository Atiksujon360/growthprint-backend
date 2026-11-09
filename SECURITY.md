# GrowthPrint Backend - Security Policies

## Overview

This document outlines security policies, authentication, authorization, secrets management, and vulnerability handling for the GrowthPrint backend. All security practices are based on OWASP ASVS and OWASP Cheat Sheet Series.

## Security Hardening Log (2026-11-02) — account security, staff access, password recovery

Branch `feature/growthprint-professional-improvements`. Migration **V20** (`V20__account_security_and_password_recovery.sql`).

| Change | Why |
|---|---|
| `users.token_version` + `ver` JWT claim; every request re-checks the account (`AccountSessionService`): must exist, be `ACTIVE`, have the same role and the same token version | Password change/reset, "sign out everywhere", suspension, deactivation and role changes now revoke existing tokens **immediately** (previously valid until expiry, up to 1 h). Pre-V20 tokens have no claim and count as version 0, so existing sessions survive the deploy |
| Authorities are loaded from the database per request, not from the token | Revoking a team member's module takes effect on their next request |
| `TEAM_MEMBER` role (V17 schema, now implemented). Module permissions: `CRM`, `CLIENTS`, `PROJECTS`, `SUPPORT`, `PROPOSALS`, `OUTREACH` → authorities `PERM_*`; URL rules in `SecurityConfig` and `@PreAuthorize` on the module controllers | Staff can work on granted modules only. Invoices/payment reports, AI settings, AI triage and user management stay ADMIN-only (not grantable) |
| User administration (`/api/v1/admin/users/**`, `/api/v1/admin/security/overview`), ADMIN only | Invite (INVITED account, no password), resend invitation, admin-issued reset link, suspend/reactivate/disable, ADMIN↔TEAM_MEMBER role change, permissions. Guard rails: no self status/role change, last active admin cannot be suspended/demoted, client logins never become staff, accounts are deactivated not deleted |
| Self-service account endpoints (`/api/v1/account/**`): profile, preferences, change password (returns a fresh token for this device, revokes all others), sign out everywhere | No user id in the path: a user can only ever address their own account; role/status/email/client membership are not editable there |
| Legacy `POST /api/v1/auth/password` (change password, revokes all sessions incl. the current one, 204) | MVP1-compatible contract |
| Password policy (`PasswordPolicy`): 12–128 chars, a letter and a digit, not a common password, must not contain the email name; applied to every **new** password | NIST SP 800-63B style; existing passwords keep working |
| Forgot password: `POST /auth/password/forgot` always answers `202` with the same message; `/reset/verify`; `/reset` | No account enumeration. Tokens: 256-bit `SecureRandom`, only SHA-256 stored (`password_reset_tokens`), single use (row lock + `used_at`), 30 min TTL, older links deleted on a new request, only ACTIVE accounts, weak password keeps the link usable |
| Invitations: `/auth/invitations/preview` and `/auth/invitations/accept` (auto sign-in) | Same token handling (SHA-256 in `user_invitations`, 72 h TTL, reissue revokes older links) |
| Links put the token in the URL **fragment** (`/reset-password#token=…`, `/accept-invite#token=…`); tokens are POSTed in JSON bodies | Fragments are not sent to servers, so tokens do not end up in access logs or `Referer` headers |
| Rate limits: shared per-IP bucket for all recovery/invitation endpoints (`app.rate-limit.account-recovery`, 10 / 15 min); per-account login throttle (20 / 15 min, keyed by SHA-256 of the email, `429` + `Retry-After`); per-account reset-email throttle (3 / h, silently ignored beyond) | Brute force from many IPs against one account, token guessing, email flooding |
| `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` on `/api/**`, `X-Frame-Options: DENY`, `Permissions-Policy`, `Cross-Origin-Opener-Policy` | Defence in depth for JSON responses (Swagger UI unaffected) |
| `SECURITY_AUDIT` logger (`SecurityAudit`): login success/failure/throttle, password change/reset, sessions revoked, invitations, status/role/permission changes | Audit trail. Events carry user ids only — never emails, passwords, tokens or links |
| New errors: weak password / invalid link / wrong current password → `400` (field errors), throttling → `429` | RFC 9457-compatible body; wrong current password is not a 401 so clients do not treat it as an expired session |

### Password recovery: required configuration

| Setting | Purpose |
|---|---|
| `FRONTEND_BASE_URL` (env) → `app.security.account.frontend-base-url` | Public frontend URL used in reset and invitation links (default `http://localhost:3000`). **Set it on Render** to the Vercel URL, e.g. `https://growth-print-frontend.vercel.app` |
| `app.security.account.password-reset-ttl` / `invitation-ttl` | Link lifetimes (30 min / 72 h) |

**Email delivery is NOT configured.** No email provider or mail dependency exists in this project (decision 18). The
default `UnconfiguredAccountEmailSender` sends nothing and logs a warning *without* the link or address. Until a
provider is connected:

- the forgot-password page still answers generically, but no email arrives;
- the admin can create a one-time reset link for a user in the studio (Users & team → "Reset link",
  `POST /api/v1/admin/users/{id}/password-reset-link`) and share it through a trusted channel;
- invitation links are shown once to the admin who created them.

To connect a provider: add a Spring bean implementing `com.growthprint.backend.auth.notification.AccountEmailSender`
(for example backed by `spring-boot-starter-mail` + SMTP credentials from environment variables, or a provider's HTTP
API). Registering any `AccountEmailSender` bean replaces the no-op one (`AccountEmailConfig`, `@ConditionalOnMissingBean`).
Adding the dependency and credentials requires the founder's approval.

### Remaining risks (not fixed in this pass)

- **Client-assignment scoping is not enforced.** V17 has `client_assignments` / `clients.owner_user_id`, but a team
  member with a module (e.g. `CLIENTS`) sees **all** clients' records in that module. Grant modules only to trusted staff.
- No refresh tokens: access tokens live 1 h; the frontend keeps the token in `sessionStorage` (XSS-readable).
  Moving to httpOnly cookies + refresh tokens is an authentication-architecture change that needs approval.
- Rate limiting is in-memory and per instance (fine for one Render instance; use a shared store before scaling out).
- Per-request account lookup adds one indexed query per authenticated request.
- Reset requests for known vs unknown emails do slightly different work (DB insert); response bodies and status are
  identical, timing is not strictly equalised.
- The seed account noted below still applies.

## Security Hardening Log (2026-11-01)

| Change | Why |
|---|---|
| Removed the hard-coded `JWT_SECRET` fallback from `application.yml`; startup now fails if `JWT_SECRET` is missing, < 32 chars, or equal to the old default | The old default was public in the repo, so anyone could have forged ADMIN tokens if the env var was ever missing |
| JWT issuer (`iss`) is now verified; key bytes use UTF-8; role claim must be a known `UserRole` | Defence in depth against foreign/forged tokens |
| Fixed `InMemoryRateLimiter` window bug (counter reset on almost every request) | Public inquiry rate limit was effectively disabled |
| Added per-IP rate limit on `POST /api/v1/auth/login` (`app.rate-limit.login`, default 10/min) with `Retry-After` | Password brute-force protection |
| `server.forward-headers-strategy: native` | Real client IP behind Render's proxy (only trusted internal proxies honoured) and HTTPS detection for HSTS |
| Login does a dummy BCrypt check for unknown emails | Prevents account enumeration via response timing |
| `GlobalExceptionHandler`: 401 bad login, 403 access denied, 400 bad input/UUID, 409 invalid state, sanitized 500 | Unmapped errors previously surfaced as misleading 401s; no internals leak |
| 401/403/429 JSON bodies written via `ApiErrorResponseWriter` | Bare `ObjectMapper` could not serialize `Instant`, so these bodies failed |
| One CORS configuration (in `SecurityConfig`), `*` origin rejected, `X-Correlation-ID` exposed | Two competing CORS configs removed |
| Swagger/OpenAPI toggle via `SPRINGDOC_ENABLED` (default `true`) | DEPLOYMENT.md already required it off in prod but it wasn't wired |
| `Referrer-Policy: no-referrer`; server error pages never include stack traces/messages | Information leakage |
| `JwtAuthenticationFilter` standalone servlet registration disabled (runs only inside the security chain) | A copy running before the chain caused every authenticated request to be 401 (found by the role-matrix test); production relied on filter ordering |
| HTTP role-matrix test: every route x anonymous/CLIENT/ADMIN with real JWTs (`RoleMatrixSecurityTest`) | ADMIN/CLIENT rules are now verified at HTTP level, not only by reading SecurityConfig |
| Client payment reports (V15): clients can report, only ADMIN verification marks an invoice PAID; `/api/v1/invoices/my/**` is CLIENT-only | Clients must never mark invoices paid |
| Ownership: other clients' support requests / invoices return 404, other clients' proposals 403 | Prevent probing and cross-client actions |
| Estimate approval records the authenticated user, not a client-supplied name | Spoofable audit trail |
| All support-request bodies validated (`@Valid`); enum errors no longer leak Java class names | 500s and internal detail leakage |
| Duplicate/constraint violations return 409 with a generic message (SQL never returned) | Information leakage, misleading 500s |
| PostgreSQL test harness refuses `render.com` hosts and overrides `DATABASE_URL` | `.env` points at the shared Render DB; tests must never migrate it |

**Open security items (documented, not changed):** seed account `admin@growthprint.dev` with a password that is
in the repo (`SeedDataCommand`, `seed` profile) - verify it does not exist on Render and rotate if it does;
JWTs stay valid up to 1 h after an account is disabled; the in-memory rate limiter is per instance.

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
