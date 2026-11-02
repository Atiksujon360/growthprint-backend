package com.growthprint.backend.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One-time invitation to join a business (V21). Only the SHA-256 hash of the token is stored. */
@Entity
@Table(name = "business_invitations")
public class BusinessInvitation {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REVOKED = "REVOKED";

    @Id
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessRole role;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by_user_id")
    private UUID acceptedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected BusinessInvitation() {
    }

    public BusinessInvitation(UUID id, UUID businessId, String normalizedEmail, BusinessRole role, String tokenHash,
                              Instant expiresAt, UUID invitedByUserId, Instant now) {
        if (role == BusinessRole.OWNER) {
            throw new IllegalArgumentException("Ownership is transferred, not granted by invitation");
        }
        this.id = id;
        this.businessId = businessId;
        this.normalizedEmail = normalizedEmail;
        this.role = role;
        this.tokenHash = tokenHash;
        this.status = PENDING;
        this.expiresAt = expiresAt;
        this.invitedByUserId = invitedByUserId;
        this.createdAt = now;
    }

    public boolean isUsable(Instant now) {
        return PENDING.equals(status) && now.isBefore(expiresAt);
    }

    public void accept(UUID userId, Instant now) {
        this.status = ACCEPTED;
        this.acceptedAt = now;
        this.acceptedByUserId = userId;
    }

    public void revoke(Instant now) {
        this.status = REVOKED;
        this.revokedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public BusinessRole getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
