package com.growthprint.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One-time account-setup invitation (V17 table user_invitations). Only the token's SHA-256 hash is stored. */
@Entity
@Table(name = "user_invitations")
public class UserInvitation {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REVOKED = "REVOKED";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserInvitation() {
    }

    public UserInvitation(UUID id, UUID userId, String tokenHash, Instant expiresAt, UUID createdByUserId,
                          Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.status = PENDING;
        this.expiresAt = expiresAt;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public boolean isUsable(Instant now) {
        return PENDING.equals(status) && now.isBefore(expiresAt);
    }

    public void accept(Instant now) {
        this.status = ACCEPTED;
        this.acceptedAt = now;
    }

    public void revoke(Instant now) {
        this.status = REVOKED;
        this.revokedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
