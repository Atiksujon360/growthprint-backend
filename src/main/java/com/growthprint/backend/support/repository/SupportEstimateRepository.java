package com.growthprint.backend.support.repository;

import com.growthprint.backend.support.entity.SupportEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportEstimateRepository extends JpaRepository<SupportEstimate, UUID> {
    Optional<SupportEstimate> findByRequestId(UUID requestId);
}
