package com.growthprint.backend.crm.lead.repository;

import com.growthprint.backend.crm.lead.entity.CrmLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrmLeadRepository extends JpaRepository<CrmLead, UUID> {

    List<CrmLead> findAllByOrderByCreatedAtDesc();

    Optional<CrmLead> findByInquiryId(UUID inquiryId);

    boolean existsByCompanyNameAndContactEmail(String companyName, String contactEmail);
}
