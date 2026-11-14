package com.growthprint.backend.auth.entity;

/** Sub-role of a CLIENT portal user inside their company (V17 CHECK constraint chk_users_client_role). */
public enum ClientRole {
    CLIENT_OWNER,
    CLIENT_PROJECT_MANAGER,
    CLIENT_FINANCE,
    CLIENT_MEMBER
}
