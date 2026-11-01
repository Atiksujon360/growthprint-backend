package com.growthprint.backend.project.dto;

import com.growthprint.backend.project.entity.MilestoneStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateMilestoneCommand(
        @NotBlank(message = "Title is required")
        @jakarta.validation.constraints.Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @NotNull(message = "Status is required")
        MilestoneStatus status
) {
}
