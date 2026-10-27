package com.growthprint.backend.proposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposalDecisionCommand(
        @NotBlank(message = "Decision is required")
        String decision, // "ACCEPT" or "DECLINE"

        @Size(max = 1000, message = "Notes must not exceed 1000 characters")
        String notes
) {}
