package com.growthprint.backend.business.entity;

/**
 * Per-business role (V21 chk_business_memberships_role), ordered from most to least privileged.
 * The same user can hold different roles in different businesses.
 */
public enum BusinessRole {
    OWNER(4),
    ADMIN(3),
    MANAGER(2),
    MEMBER(1);

    private final int rank;

    BusinessRole(int rank) {
        this.rank = rank;
    }

    /** True when this role is at least as privileged as {@code other}. */
    public boolean atLeast(BusinessRole other) {
        return rank >= other.rank;
    }

    /** True when this role is strictly more privileged than {@code other}. */
    public boolean outranks(BusinessRole other) {
        return rank > other.rank;
    }
}
