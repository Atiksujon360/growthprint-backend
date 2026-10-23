package com.growthprint.backend.invoice.dto;

import java.math.BigDecimal;

public record InvoiceLineItemResponse(
        String id,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
