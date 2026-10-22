package com.growthprint.backend.crm.lead.dto;

import com.growthprint.backend.crm.lead.entity.CrmLead;
import com.growthprint.backend.crm.lead.entity.CrmLeadEnums;

import java.time.Instant;
import java.util.UUID;

public record CrmLeadResponse(
        UUID id,
        String companyName,
        String website,
        String industry,
        String location,
        String contactName,
        String contactEmail,
        String contactTitle,
        CrmLeadEnums.CrmLeadSource source,
        String sourceUrl,
        Instant discoveredDate,
        CrmLeadEnums.CrmLeadServiceCategory targetService,
        String identifiedProblem,
        String researchSummary,
        Integer qualificationScore,
        Integer businessFitScore,
        Integer evidenceOfNeedScore,
        Integer contactabilityScore,
        Integer serviceRelevanceScore,
        CrmLeadEnums.CrmLeadPipelineStage pipelineStage,
        Instant lastContactDate,
        String nextAction,
        Boolean doNotContact,
        String notes,
        UUID inquiryId,
        Instant createdAt,
        Instant updatedAt
) {

    public static CrmLeadResponse from(CrmLead crmLead) {
        return new CrmLeadResponse(
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
                crmLead.getPipelineStage(),
                crmLead.getLastContactDate(),
                crmLead.getNextAction(),
                crmLead.getDoNotContact(),
                crmLead.getNotes(),
                crmLead.getInquiryId(),
                crmLead.getCreatedAt(),
                crmLead.getUpdatedAt()
        );
    }
}
