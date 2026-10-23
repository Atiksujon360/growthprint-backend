package com.growthprint.backend.invoice.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceResponse(
        String id,
        String invoiceNumber,
        String clientId,
        String clientName,
        String clientEmail,
        String issueDate,
        String dueDate,
        List<InvoiceLineItemResponse> lineItems,
        BigDecimal subtotal,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        String status,
        String paidAt,
        String notes,
        String updatedAt
) {
}
