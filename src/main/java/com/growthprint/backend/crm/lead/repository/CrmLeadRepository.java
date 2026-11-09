package com.growthprint.backend.crm.lead.repository;

import com.growthprint.backend.crm.lead.entity.CrmLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrmLeadRepository extends JpaRepository<CrmLead, UUID> {

    /** Row lock so two concurrent conversions of the same lead cannot both create a client. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from CrmLead l where l.id = :id")
    java.util.Optional<CrmLead> findForUpdateById(UUID id);


    List<CrmLead> findAllByOrderByCreatedAtDesc();

    Optional<CrmLead> findByInquiryId(UUID inquiryId);

    boolean existsByCompanyNameAndContactEmail(String companyName, String contactEmail);
}
