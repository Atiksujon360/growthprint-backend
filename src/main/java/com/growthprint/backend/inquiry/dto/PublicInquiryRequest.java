package com.growthprint.backend.inquiry.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.growthprint.backend.lead.entity.LeadEnums.PreferredContactMethod;
import static com.growthprint.backend.lead.entity.LeadEnums.ServiceCategory;

public record PublicInquiryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String businessEmail,
        @NotBlank @Size(max = 160) String company,
        @Size(max = 2048)
        @Pattern(regexp = "^$|https?://.+", message = "must be an HTTP or HTTPS URL") String website,
        @NotNull ServiceCategory serviceCategory,
        @NotBlank @Size(max = 160) String projectType,
        @Size(max = 120) String budgetRange,
        @Size(max = 120) String preferredTimeline,
        @NotBlank @Size(min = 15, max = 5000) String projectDescription,
        @NotNull PreferredContactMethod preferredContactMethod,
        @AssertTrue(message = "consent must be true") boolean consent
) {
}
