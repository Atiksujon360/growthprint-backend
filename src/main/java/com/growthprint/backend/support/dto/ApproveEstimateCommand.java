package com.growthprint.backend.support.dto;

import jakarta.validation.constraints.NotBlank;

public record ApproveEstimateCommand(
        @NotBlank(message = "Approved by is required")
        @jakarta.validation.constraints.Size(max = 200, message = "Approved by must not exceed 200 characters")
        String approvedBy
) {
}
