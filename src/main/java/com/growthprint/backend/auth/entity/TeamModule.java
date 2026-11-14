package com.growthprint.backend.auth.entity;

/**
 * Internal modules a TEAM_MEMBER can be granted (V17 table team_member_permissions).
 * Finance (invoices, payment reports), AI settings and user management are deliberately not grantable:
 * they stay ADMIN-only.
 */
public enum TeamModule {
    CRM,
    CLIENTS,
    PROJECTS,
    SUPPORT,
    PROPOSALS,
    OUTREACH;

    /** Spring Security authority granted to team members with this module, e.g. {@code PERM_CRM}. */
    public String authority() {
        return "PERM_" + name();
    }
}
