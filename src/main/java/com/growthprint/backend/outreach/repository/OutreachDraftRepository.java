package com.growthprint.backend.outreach.repository;

import com.growthprint.backend.outreach.entity.OutreachDraft;
import com.growthprint.backend.outreach.entity.OutreachDraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutreachDraftRepository extends JpaRepository<OutreachDraft, UUID> {

    List<OutreachDraft> findByLeadId(UUID leadId);

    List<OutreachDraft> findByStatus(OutreachDraftStatus status);

    Optional<OutreachDraft> findByLeadIdAndId(UUID leadId, UUID id);
}
