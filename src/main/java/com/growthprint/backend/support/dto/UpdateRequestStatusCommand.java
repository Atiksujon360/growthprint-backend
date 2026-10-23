package com.growthprint.backend.support.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRequestStatusCommand(
        @NotBlank(message = "Status is required")
        String status
) {
}
