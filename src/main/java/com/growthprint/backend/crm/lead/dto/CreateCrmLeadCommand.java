package com.growthprint.backend.crm.lead.dto;

import com.growthprint.backend.crm.lead.entity.CrmLeadEnums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCrmLeadCommand(
        @NotBlank @Size(max = 160) String companyName,
        @NotBlank @Size(max = 2048) String website,
        @NotBlank @Size(max = 120) String industry,
        @NotBlank @Size(max = 120) String location,
        @NotBlank @Size(max = 120) String contactName,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @Size(max = 120) String contactTitle,
        @NotNull CrmLeadEnums.CrmLeadSource source,
        @Size(max = 2048) String sourceUrl,
        @NotNull CrmLeadEnums.CrmLeadServiceCategory targetService,
        @NotBlank @Size(max = 2000) String identifiedProblem,
        @NotBlank @Size(max = 5000) String researchSummary,
        @NotNull @Min(0) @Max(100) Integer qualificationScore,
        @Min(0) @Max(25) Integer businessFitScore,
        @Min(0) @Max(25) Integer evidenceOfNeedScore,
        @Min(0) @Max(25) Integer contactabilityScore,
        @Min(0) @Max(25) Integer serviceRelevanceScore,
        @NotNull CrmLeadEnums.CrmLeadPipelineStage pipelineStage,
        @NotBlank @Size(max = 500) String nextAction,
        @NotNull Boolean doNotContact,
        @Size(max = 5000) String notes,
        UUID inquiryId
) {
}
