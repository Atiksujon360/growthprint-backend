package com.growthprint.backend.project.dto;

public record MilestoneResponse(
        String id,
        String title,
        String dueDate,
        String status
) {
}
