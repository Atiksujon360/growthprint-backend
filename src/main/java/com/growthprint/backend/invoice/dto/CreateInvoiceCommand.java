package com.growthprint.backend.invoice.dto;

import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateInvoiceCommand(
        @NotBlank(message = "Invoice number is required")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "Invoice number must be uppercase alphanumeric with hyphens")
        @jakarta.validation.constraints.Size(max = 50, message = "Invoice number must not exceed 50 characters")
        String invoiceNumber,

        @NotBlank(message = "Client ID is required")
        String clientId,

        @NotBlank(message = "Client name is required")
        @jakarta.validation.constraints.Size(max = 200, message = "Client name must not exceed 200 characters")
        String clientName,

        @NotBlank(message = "Client email is required")
        @jakarta.validation.constraints.Email(message = "Client email must be valid")
        @jakarta.validation.constraints.Size(max = 200, message = "Client email must not exceed 200 characters")
        String clientEmail,

        @NotNull(message = "Issue date is required")
        LocalDate issueDate,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @NotNull(message = "Subtotal is required")
        @PositiveOrZero(message = "Subtotal must be positive or zero")
        BigDecimal subtotal,

        @NotNull(message = "Tax percent is required")
        @PositiveOrZero(message = "Tax percent must be positive or zero")
        BigDecimal taxPercent,

        @NotNull(message = "Tax amount is required")
        @PositiveOrZero(message = "Tax amount must be positive or zero")
        BigDecimal taxAmount,

        @NotNull(message = "Total amount is required")
        @PositiveOrZero(message = "Total amount must be positive or zero")
        BigDecimal totalAmount,

        @NotNull(message = "Currency is required")
        InvoiceCurrency currency,

        @NotNull(message = "Status is required (DRAFT or ISSUED)")
        InvoiceStatus status,

        String notes,

        @NotNull(message = "Line items are required")
        List<@jakarta.validation.Valid CreateInvoiceLineItemCommand> lineItems
) {
    public record CreateInvoiceLineItemCommand(
            @NotBlank(message = "Description is required")
            String description,

            @NotNull(message = "Quantity is required")
            @jakarta.validation.constraints.Positive(message = "Quantity must be positive")
            Integer quantity,

            @NotNull(message = "Unit price is required")
            @PositiveOrZero(message = "Unit price must be positive or zero")
            BigDecimal unitPrice,

            @NotNull(message = "Amount is required")
            @PositiveOrZero(message = "Amount must be positive or zero")
            BigDecimal amount
    ) {}
}
