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
     * Latest estimate for a request. Since V16, support_estimates.request_id is UNIQUE (at most one estimate per
     * request; re-classifying deletes the old one), so this and {@link #findByRequestId(UUID)} return the same row.
     * Kept for reads that must not fail even if V16 is not yet applied to a database.
     */
    Optional<SupportEstimate> findFirstByRequestIdOrderByCreatedAtDesc(UUID requestId);

    /** Batch read used when rendering lists (avoids one query per support request). */
    java.util.List<SupportEstimate> findByRequestIdIn(java.util.Collection<UUID> requestIds);
}
