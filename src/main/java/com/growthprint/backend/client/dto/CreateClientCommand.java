package com.growthprint.backend.client.dto;

import com.growthprint.backend.client.entity.ClientEnums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** POST /api/v1/clients. Optional fields get safe defaults in ClientService.create. */
public record CreateClientCommand(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,
        @Size(max = 160, message = "Legal name must be at most 160 characters")
        String legalName,
        @NotBlank(message = "Contact email is required")
        @Email(message = "Contact email must be valid")
        @Size(max = 254, message = "Contact email is too long")
        String contactEmail,
        @Size(max = 50, message = "Phone must be at most 50 characters")
        String phone,
        @Size(max = 2048, message = "Website is too long")
        String website,
        ClientEnums.BillingCurrency billingCurrency,
        ClientEnums.RetainerTier retainerTier,
        @Min(value = 0, message = "Retainer hours cannot be negative")
        @Max(value = 744, message = "Retainer hours are too high")
        Integer monthlyRetainerHours,
        LocalDate contractStartDate,
        @Size(max = 120, message = "Account manager must be at most 120 characters")
        String accountManager,
        @Size(max = 5000, message = "Notes are too long")
        String notes) {
}
