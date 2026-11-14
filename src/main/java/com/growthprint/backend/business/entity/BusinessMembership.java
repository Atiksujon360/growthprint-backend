package com.growthprint.backend.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A user's access to one business with a per-business role (V21 table business_memberships). */
@Entity
@Table(name = "business_memberships")
public class BusinessMembership {

    public static final String ACTIVE = "ACTIVE";
    public static final String REMOVED = "REMOVED";

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessRole role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BusinessMembership() {
    }

    public BusinessMembership(UUID businessId, UUID userId, BusinessRole role, UUID invitedByUserId, Instant now) {
        this.id = new Key(businessId, userId);
        this.role = role;
        this.status = ACTIVE;
        this.invitedByUserId = invitedByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void changeRole(BusinessRole newRole) {
        this.role = newRole;
    }

    public void remove() {
        this.status = REMOVED;
    }

    /** Re-admits a previously removed member (for example through a new invitation). */
    public void reactivate(BusinessRole newRole, UUID invitedBy) {
        this.status = ACTIVE;
        this.role = newRole;
        this.invitedByUserId = invitedBy;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public UUID getBusinessId() {
        return id.businessId;
    }

    public UUID getUserId() {
        return id.userId;
    }

    public BusinessRole getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "business_id", nullable = false)
        private UUID businessId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected Key() {
        }

        public Key(UUID businessId, UUID userId) {
            this.businessId = businessId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(businessId, k.businessId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(businessId, userId);
        }
    }
}
