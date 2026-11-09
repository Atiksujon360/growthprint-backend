package com.growthprint.backend.auth.entity;

/**
 * Account lifecycle (V17 CHECK constraint chk_users_account_status). Only ACTIVE accounts can sign in;
 * every other status also invalidates existing tokens on the next request.
 */
public enum AccountStatus {
    /** Created by an admin; the user has not set a password through the invitation link yet. */
    INVITED,
    ACTIVE,
    /** Temporarily blocked by an admin (can be re-activated). */
    SUSPENDED,
    /** Switched off by an admin. */
    DISABLED
}
