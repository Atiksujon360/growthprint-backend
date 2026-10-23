package com.growthprint.backend.invoice.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceLineItemResponse(
        String id,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
