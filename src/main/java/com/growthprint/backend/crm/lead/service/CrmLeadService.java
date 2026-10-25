package com.growthprint.backend.crm.lead.service;

import com.growthprint.backend.crm.lead.dto.CreateCrmLeadCommand;
import com.growthprint.backend.crm.lead.dto.CrmLeadResponse;
import com.growthprint.backend.crm.lead.dto.UpdatePipelineStageCommand;
import com.growthprint.backend.crm.lead.entity.CrmLead;
import com.growthprint.backend.crm.lead.exception.CrmLeadAlreadyExistsException;
import com.growthprint.backend.crm.lead.exception.CrmLeadConversionException;
import com.growthprint.backend.crm.lead.exception.CrmLeadNotFoundException;
import com.growthprint.backend.crm.lead.exception.InquiryNotFoundException;
import com.growthprint.backend.crm.lead.repository.CrmLeadRepository;
import com.growthprint.backend.lead.entity.Lead;
import com.growthprint.backend.lead.repository.LeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadSource;

@Service
public class CrmLeadService {

    private final CrmLeadRepository crmLeadRepository;
    private final LeadRepository leadRepository;
    private final Clock clock;

    @Autowired
    public CrmLeadService(CrmLeadRepository crmLeadRepository, LeadRepository leadRepository, Clock clock) {
        this.crmLeadRepository = crmLeadRepository;
        this.leadRepository = leadRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public List<CrmLeadResponse> findAll() {
        return crmLeadRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(CrmLeadResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CrmLeadResponse findById(UUID id) {
        CrmLead crmLead = crmLeadRepository.findById(id)
                .orElseThrow(() -> new CrmLeadNotFoundException(id));
        return CrmLeadResponse.from(crmLead);
    }

    @Transactional
    public CrmLeadResponse create(CreateCrmLeadCommand command) {
        Instant now = clock.instant();
        CrmLead crmLead = new CrmLead(
                UUID.randomUUID(),
                command.companyName(),
                command.website(),
                command.industry(),
                command.location(),
                command.contactName(),
                command.contactEmail(),
                command.contactTitle(),
                command.source(),
                command.sourceUrl(),
                now,
                command.targetService(),
                command.identifiedProblem(),
                command.researchSummary(),
                command.qualificationScore(),
                command.businessFitScore(),
                command.evidenceOfNeedScore(),
                command.contactabilityScore(),
                command.serviceRelevanceScore(),
                command.pipelineStage(),
                null,
                command.nextAction(),
                command.doNotContact(),
                command.notes(),
                command.inquiryId(),
                now,
                now
        );

        return CrmLeadResponse.from(crmLeadRepository.save(crmLead));
    }

    @Transactional
    public CrmLeadResponse convertFromInquiry(UUID inquiryId, CreateCrmLeadCommand command) {
        // Check if CRM lead already exists for this inquiry
        if (crmLeadRepository.findByInquiryId(inquiryId).isPresent()) {
            throw new CrmLeadAlreadyExistsException("CRM lead already exists for inquiry: " + inquiryId);
        }

        // Verify inquiry exists
        Lead inquiry = leadRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryNotFoundException(inquiryId));

        // Respect consent from original inquiry
        if (!inquiry.isConsent()) {
            throw new CrmLeadConversionException("Cannot convert inquiry without consent");
        }

        Instant now = clock.instant();
        CrmLead crmLead = new CrmLead(
                UUID.randomUUID(),
                command.companyName(),
                command.website(),
                command.industry(),
                command.location(),
                command.contactName(),
                command.contactEmail(),
                command.contactTitle(),
                CrmLeadSource.INBOUND_INQUIRY,
                command.sourceUrl(),
                now,
                command.targetService(),
                command.identifiedProblem(),
                command.researchSummary(),
                command.qualificationScore(),
                command.businessFitScore(),
                command.evidenceOfNeedScore(),
                command.contactabilityScore(),
                command.serviceRelevanceScore(),
                command.pipelineStage(),
                null,
                command.nextAction(),
                command.doNotContact(),
                command.notes(),
                inquiryId,
                now,
                now
        );

        return CrmLeadResponse.from(crmLeadRepository.save(crmLead));
    }

    @Transactional
    public CrmLeadResponse updatePipelineStage(UUID id, UpdatePipelineStageCommand command) {
        CrmLead crmLead = crmLeadRepository.findById(id)
                .orElseThrow(() -> new CrmLeadNotFoundException(id));

        crmLead = new CrmLead(
                crmLead.getId(),
                crmLead.getCompanyName(),
                crmLead.getWebsite(),
                crmLead.getIndustry(),
                crmLead.getLocation(),
                crmLead.getContactName(),
                crmLead.getContactEmail(),
                crmLead.getContactTitle(),
                crmLead.getSource(),
                crmLead.getSourceUrl(),
                crmLead.getDiscoveredDate(),
                crmLead.getTargetService(),
                crmLead.getIdentifiedProblem(),
                crmLead.getResearchSummary(),
                crmLead.getQualificationScore(),
                crmLead.getBusinessFitScore(),
                crmLead.getEvidenceOfNeedScore(),
                crmLead.getContactabilityScore(),
                crmLead.getServiceRelevanceScore(),
                command.pipelineStage(),
                clock.instant(),
                crmLead.getNextAction(),
                crmLead.getDoNotContact(),
                crmLead.getNotes(),
                crmLead.getInquiryId(),
                crmLead.getCreatedAt(),
                clock.instant()
        );

        return CrmLeadResponse.from(crmLeadRepository.save(crmLead));
    }

    @Transactional
    public void delete(UUID id) {
        CrmLead crmLead = crmLeadRepository.findById(id)
                .orElseThrow(() -> new CrmLeadNotFoundException(id));
        crmLeadRepository.delete(crmLead);
    }
}
