package com.growthprint.backend.support.repository;

import com.growthprint.backend.support.entity.SupportEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportEstimateRepository extends JpaRepository<SupportEstimate, UUID> {
    Optional<SupportEstimate> findByRequestId(UUID requestId);

    /**
     * Tolerant read: support_estimates has no unique constraint on request_id, so historic data may hold
     * more than one row per request. Always use this instead of {@link #findByRequestId(UUID)}.
     */
    Optional<SupportEstimate> findFirstByRequestIdOrderByCreatedAtDesc(UUID requestId);

    /** Batch read used when rendering lists (avoids one query per support request). */
    java.util.List<SupportEstimate> findByRequestIdIn(java.util.Collection<UUID> requestIds);
}
