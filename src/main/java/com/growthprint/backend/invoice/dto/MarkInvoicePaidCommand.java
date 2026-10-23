package com.growthprint.backend.invoice.dto;

import com.growthprint.backend.invoice.entity.InvoiceStatus;

public record MarkInvoicePaidCommand(
        @jakarta.validation.constraints.NotNull(message = "Status is required")
        InvoiceStatus status
) {
}
