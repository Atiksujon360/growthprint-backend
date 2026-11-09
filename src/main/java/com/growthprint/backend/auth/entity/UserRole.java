package com.growthprint.backend.auth.entity;

/**
 * Authenticated roles (V17 CHECK constraint chk_users_role).
 * <ul>
 *     <li>ADMIN - the agency owner; full access, including finance, AI settings and user management.</li>
 *     <li>TEAM_MEMBER - staff; access only to the internal modules an admin granted ({@link TeamModule}).</li>
 *     <li>CLIENT - client portal user; only their own company data through the {@code /my} endpoints.</li>
 * </ul>
 */
public enum UserRole {
    ADMIN,
    TEAM_MEMBER,
    CLIENT
}
