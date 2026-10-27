package com.growthprint.backend.proposal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProposalResponse(
        String id,
        String proposalNumber,
        String clientId,
        String clientName,
        String title,
        String serviceCategory,
        String scopeSummary,
        List<String> deliverables,
        List<String> exclusions,
        BigDecimal totalPrice,
        String currency,
        List<PaymentMilestone> paymentMilestones,
        String timelineDuration,
        String validUntil,
        String status,
        String acceptedAt,
        String acceptedBy,
        String declinedAt,
        String declinedBy,
        String notes,
        String createdAt,
        String updatedAt
) {
    public record PaymentMilestone(
            String description,
            Integer percentage,
            BigDecimal amount
    ) {}
}
