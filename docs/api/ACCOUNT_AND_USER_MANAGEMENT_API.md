# Account, recovery and user-management API (2026-11-02)

All bodies are JSON. Errors use the RFC 9457-compatible `ApiErrorResponse` (`message`, `fieldErrors`).

## Public (no token, rate limited per IP: `app.rate-limit.account-recovery`)

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/api/v1/auth/password/policy` | – | 200 `{minLength,maxLength,description}` | |
| POST | `/api/v1/auth/password/forgot` | `{email}` | 202 `{message}` | Same answer for every address |
| POST | `/api/v1/auth/password/reset/verify` | `{token}` | 204 | 400 if invalid/expired/used |
| POST | `/api/v1/auth/password/reset` | `{token,newPassword}` | 204 | Revokes all sessions; 400 weak password (`fieldErrors.newPassword`) or bad link |
| POST | `/api/v1/auth/invitations/preview` | `{token}` | 200 `{email,fullName,role,clientName}` | |
| POST | `/api/v1/auth/invitations/accept` | `{token,password}` | 200 `LoginResponse` | Activates and signs in |

## Signed-in user (own account only)

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/auth/me` | – | 200 profile (original fields + `fullName`, `accountStatus`, `clientId`, `permissions`, …) |
| GET/PATCH | `/api/v1/account/profile` | `{fullName,phone,jobTitle,timezone,emailNotifications}` | 200 profile |
| POST | `/api/v1/account/password` | `{currentPassword,newPassword}` | 200 `LoginResponse` (fresh token; other sessions revoked) |
| POST | `/api/v1/auth/password` | same | 204 (legacy; revokes every session incl. this one) |
| POST | `/api/v1/account/sessions/revoke` | – | 204 (sign out everywhere) |

`LoginResponse.user` now also has `fullName` and `permissions` (additive).

## Admin only

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/admin/users?role=&clientId=` | – | 200 `ManagedUser[]` (incl. `clientName`) |
| GET | `/api/v1/admin/users/{id}` | – | 200 |
| POST | `/api/v1/admin/users` | `{email,fullName,role,clientId?,clientRole?,permissions?}` | 201 `{user, invitation:{token,link,expiresAt}}` |
| POST | `/api/v1/admin/users/{id}/invitation` | – | 200 `{token,link,expiresAt}` |
| POST | `/api/v1/admin/users/{id}/password-reset-link` | – | 200 `{token,link,expiresAt}` |
| POST | `/api/v1/admin/users/{id}/suspend` / `reactivate` | – | 200 |
| PATCH | `/api/v1/admin/users/{id}/status` | `{status}` | 200 |
| PATCH | `/api/v1/admin/users/{id}/role` | `{role: ADMIN|TEAM_MEMBER}` | 200 |
| PUT | `/api/v1/admin/users/{id}/permissions` | `{modules:[...]}` | 200 |
| GET | `/api/v1/admin/security/overview` | – | 200 |

Errors: 400 invalid input / self-change, 404 unknown user, 409 duplicate email or last-admin protection.

## Team-member module access

| Module | Paths |
|---|---|
| CRM | `/api/v1/leads/**`, `/api/v1/crm-leads/**` |
| CLIENTS | `/api/v1/clients/**` |
| PROJECTS | `/api/v1/projects/**` (not `/my`) |
| SUPPORT | `/api/v1/support-requests/**` (not `/my`, not `/{id}/ai/**`) |
| PROPOSALS | `/api/v1/proposals/**` (not `/my`) |
| OUTREACH | `/api/v1/outreach/**` |
| never | `/api/v1/invoices/**`, `/api/v1/admin/**`, AI triage |
