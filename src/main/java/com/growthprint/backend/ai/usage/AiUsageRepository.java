package com.growthprint.backend.ai.usage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsageRecord, UUID> {

    /** Calls made by one user since a point in time (used for the per-user daily limit). */
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant since);
}
