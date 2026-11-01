package com.growthprint.backend.invoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * A client payment report. Dates/timestamps are ISO-8601 strings and ids are strings, matching the other
 * invoice responses. {@code invoiceStatus} is the invoice's status after the operation (PAID after verify).
 */
@Schema(description = "Client payment report and its review state")
public record PaymentReportResponse(
        String id,
        String invoiceId,
        String invoiceNumber,
        @Schema(description = "Invoice status after this operation", example = "ISSUED")
        String invoiceStatus,
        String clientId,
        BigDecimal amount,
        String currency,
        @Schema(example = "BANK_TRANSFER") String paymentMethod,
        String reference,
        @Schema(example = "2026-11-01") String paidOn,
        String note,
        @Schema(description = "PENDING | VERIFIED | REJECTED", example = "PENDING") String status,
        String reportedByUserId,
        String reportedAt,
        String reviewedAt,
        String reviewNote
) {
}
