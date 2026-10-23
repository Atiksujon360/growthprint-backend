package com.growthprint.backend.support.dto;

import java.util.List;

public record SupportRequestResponse(
        String id,
        String reference,
        String clientId,
        String clientName,
        String projectId,
        String projectName,
        String title,
        String category,
        String description,
        String businessImpact,
        String urgency,
        String status,
        String adminNotes,
        String resolutionSummary,
        String createdAt,
        String updatedAt,
        SupportEstimateResponse estimate
) {
    public record SupportEstimateResponse(
            String id,
            String requestId,
            String estimatedHours,
            String hourlyRate,
            String subtotal,
            String discount,
            String totalAmount,
            String currency,
            Integer deliveryTimelineDays,
            String explanation,
            String status,
            String createdAt,
            String acceptedAt,
            String acceptedBy
    ) {
    }
}
