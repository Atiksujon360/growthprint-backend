package com.growthprint.backend.support.repository;

import com.growthprint.backend.support.entity.SupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, UUID> {
    Optional<SupportRequest> findByReference(String reference);

    List<SupportRequest> findByClientId(UUID clientId);
}
