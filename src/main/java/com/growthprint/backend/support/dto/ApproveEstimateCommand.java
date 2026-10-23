package com.growthprint.backend.support.dto;

import jakarta.validation.constraints.NotBlank;

public record ApproveEstimateCommand(
        @NotBlank(message = "Approved by is required")
        String approvedBy
) {
}
