package com.growthprint.backend.project.dto;

import com.growthprint.backend.project.entity.ProjectStatus;
import com.growthprint.backend.project.entity.ServiceCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateProjectCommand(
        @NotBlank(message = "Reference is required")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "Reference must be uppercase alphanumeric with hyphens")
        String reference,

        @NotBlank(message = "Project name is required")
        String name,

        @NotNull(message = "Client ID is required")
        String clientId,

        @NotNull(message = "Service category is required")
        ServiceCategory serviceCategory,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Status is required")
        ProjectStatus status,

        @NotNull(message = "Progress percent is required")
        Integer progressPercent,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "Target completion date is required")
        LocalDate targetCompletionDate,

        @NotNull(message = "Budget total is required")
        BigDecimal budgetTotal,

        BigDecimal budgetSpent,

        String assignedLead,

        List<String> deliverables
) {
    public CreateProjectCommand {
        if (budgetSpent == null) {
            budgetSpent = BigDecimal.ZERO;
        }
        if (deliverables == null) {
            deliverables = List.of();
        }
    }
}
