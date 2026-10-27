package com.growthprint.backend.support.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMySupportRequestCommand(
        String projectId,

        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Category is required")
        String category,

        @NotBlank(message = "Description is required")
        String description,

        @NotBlank(message = "Business impact is required")
        String businessImpact,

        @NotBlank(message = "Urgency is required")
        String urgency
) {
}
