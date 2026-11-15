package com.growthprint.backend.ai.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsageRecord, UUID> {

    /** Calls made by one user since a point in time (used for the per-user daily limit). */
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant since);

    /**
     * Calls that count against the daily limit: everything except the outcomes in {@code notCounted}. Calls that failed
     * only because the admin's configuration is wrong (bad key, unknown model) are not the user's usage.
     */
    @Query("select count(r) from AiUsageRecord r where r.userId = :userId and r.createdAt >= :since "
            + "and r.outcome not in :notCounted")
    long countTowardsLimit(UUID userId, Instant since, Collection<String> notCounted);
}
