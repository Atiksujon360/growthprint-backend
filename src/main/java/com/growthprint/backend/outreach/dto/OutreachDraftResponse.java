package com.growthprint.backend.outreach.dto;

import java.util.List;

public record OutreachDraftResponse(
        String id,
        String leadId,
        String companyName,
        String recipientName,
        String recipientEmail,
        String subject,
        String messageBody,
        String personalizationEvidence,
        String relevantService,
        List<String> riskFlags,
        String status,
        Boolean approvedByFounder,
        String approvedAt,
        String sentAt,
        String updatedAt
) {
}
