package com.growthprint.backend.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin-created support request on behalf of a client. Enum values as in {@link CreateMySupportRequestCommand}.
 */
public record CreateSupportRequestCommand(
        @NotBlank(message = "Client ID is required")
        @Size(max = 36, message = "Client ID must be a UUID")
        String clientId,

        @Size(max = 36, message = "Project ID must be a UUID")
        String projectId,

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category is invalid")
        String category,

        @NotBlank(message = "Description is required")
        @Size(max = 10000, message = "Description must not exceed 10000 characters")
        String description,

        @NotBlank(message = "Business impact is required")
        @Size(max = 50, message = "Business impact is invalid")
        String businessImpact,

        @NotBlank(message = "Urgency is required")
        @Size(max = 50, message = "Urgency is invalid")
        String urgency
) {
}
