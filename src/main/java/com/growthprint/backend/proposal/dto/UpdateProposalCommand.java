package com.growthprint.backend.proposal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateProposalCommand(
        @NotBlank(message = "Title is required")
        @Size(max = 300, message = "Title must not exceed 300 characters")
        String title,

        @NotBlank(message = "Service category is required")
        String serviceCategory,

        @NotBlank(message = "Scope summary is required")
        @Size(min = 10, max = 5000, message = "Scope summary must be between 10 and 5000 characters")
        String scopeSummary,

        @NotEmpty(message = "Deliverables are required")
        List<@NotBlank String> deliverables,

        List<String> exclusions,

        @NotNull(message = "Total price is required")
        @DecimalMin(value = "0.01", message = "Total price must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Total price must be a valid monetary amount")
        BigDecimal totalPrice,

        @NotBlank(message = "Currency is required")
        String currency,

        @NotEmpty(message = "Payment milestones are required")
        List<CreateProposalCommand.PaymentMilestone> paymentMilestones,

        @NotBlank(message = "Timeline duration is required")
        @Size(max = 100, message = "Timeline duration must not exceed 100 characters")
        String timelineDuration,

        @NotNull(message = "Valid until date is required")
        @Future(message = "Valid until date must be in the future")
        LocalDate validUntil,

        String notes
) {}
