package com.growthprint.backend.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A tenant (V21 table businesses). */
@Entity
@Table(name = "businesses")
public class Business {

    /** The business all MVP1 data belongs to (created by V21). */
    public static final UUID DEFAULT_BUSINESS_ID = UUID.fromString("6a1e0b7c-0000-4000-8000-00000000b001");

    public static final String ACTIVE = "ACTIVE";

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Business() {
    }

    public Business(UUID id, String name, String slug, UUID createdByUserId, Instant now) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = ACTIVE;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getStatus() {
        return status;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
