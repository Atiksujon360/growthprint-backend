package com.growthprint.backend.inquiry.dto;

import com.growthprint.backend.lead.entity.LeadEnums;

import java.time.Instant;

public record InquiryAcceptedResponse(String referenceNumber, LeadEnums.LeadStatus status,
        Instant receivedAt) {
}
