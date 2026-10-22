package com.growthprint.backend.lead.dto;

import com.growthprint.backend.lead.entity.Lead;
import com.growthprint.backend.lead.entity.LeadEnums;

import java.time.Instant;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        String referenceNumber,
        String name,
        String businessEmail,
        String company,
        String website,
        LeadEnums.ServiceCategory serviceCategory,
        String projectType,
        String budgetRange,
        String preferredTimeline,
        String projectDescription,
        LeadEnums.PreferredContactMethod preferredContactMethod,
        LeadEnums.LeadStatus status,
        Instant receivedAt
) {

    public static LeadResponse from(Lead lead) {
        return new LeadResponse(lead.getId(), lead.getReferenceNumber(), lead.getName(), lead.getBusinessEmail(),
                lead.getCompany(), lead.getWebsite(), lead.getServiceCategory(), lead.getProjectType(),
                lead.getBudgetRange(), lead.getPreferredTimeline(), lead.getProjectDescription(),
                lead.getPreferredContactMethod(), lead.getStatus(), lead.getReceivedAt());
    }
}
