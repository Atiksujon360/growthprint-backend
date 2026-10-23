package com.growthprint.backend.project.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProjectResponse(
        String id,
        String reference,
        String name,
        String clientId,
        String clientName,
        String serviceCategory,
        String description,
        String status,
        Integer progressPercent,
        String startDate,
        String targetCompletionDate,
        BigDecimal budgetTotal,
        BigDecimal budgetSpent,
        List<MilestoneResponse> milestones,
        List<String> deliverables,
        String assignedLead,
        String updatedAt
) {
}
