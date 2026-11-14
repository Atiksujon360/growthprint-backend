package com.growthprint.backend.crm.lead.dto;

import com.growthprint.backend.client.entity.ClientEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** POST /api/v1/crm-leads/{id}/convert-to-client. All fields optional; name and contact come from the lead. */
public record ConvertLeadToClientCommand(
        @Size(max = 160, message = "Legal name must be at most 160 characters")
        String legalName,
        ClientEnums.BillingCurrency billingCurrency,
        ClientEnums.RetainerTier retainerTier,
        @Min(value = 0, message = "Retainer hours cannot be negative")
        @Max(value = 744, message = "Retainer hours are too high")
        Integer monthlyRetainerHours,
        LocalDate contractStartDate,
        @Size(max = 120, message = "Account manager must be at most 120 characters")
        String accountManager) {
}
