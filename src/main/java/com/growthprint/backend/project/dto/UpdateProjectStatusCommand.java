package com.growthprint.backend.project.dto;

import com.growthprint.backend.project.entity.ProjectStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateProjectStatusCommand(
        @NotNull(message = "Status is required")
        ProjectStatus status
) {
}
