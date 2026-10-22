package com.growthprint.backend.lead.dto;

import com.growthprint.backend.lead.entity.LeadEnums;

import static com.growthprint.backend.lead.entity.LeadEnums.PreferredContactMethod;
import static com.growthprint.backend.lead.entity.LeadEnums.ServiceCategory;

/**
 * Internal command for creating a lead from an inquiry.
 * Used by the lead service layer to encapsulate lead creation logic.
 */
public record CreateLeadCommand(
        String name,
        String businessEmail,
        String company,
        String website,
        ServiceCategory serviceCategory,
        String projectType,
        String budgetRange,
        String preferredTimeline,
        String projectDescription,
        PreferredContactMethod preferredContactMethod,
        boolean consent
) {
}
