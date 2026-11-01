package com.growthprint.backend.invoice.dto;

import com.growthprint.backend.invoice.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body of POST /api/v1/invoices/my/{id}/reconcile (CLIENT). Reports a payment for admin verification;
 * it never changes the invoice status. Currency is taken from the invoice server-side.
 */
@Schema(description = "Client payment report; the invoice stays unchanged until an admin verifies it")
public record ReportPaymentCommand(
        @Schema(description = "Amount the client says they paid", example = "1250.00")
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount must be a valid monetary amount")
        BigDecimal amount,

        @Schema(description = "BANK_TRANSFER | CARD | CHECK | OTHER", example = "BANK_TRANSFER")
        @NotNull(message = "Payment method is required (BANK_TRANSFER, CARD, CHECK or OTHER)")
        PaymentMethod paymentMethod,

        @Schema(description = "Bank/transaction reference, optional", example = "ACH-20261101-7781")
        @Size(max = 120, message = "Reference must not exceed 120 characters")
        String reference,

        @Schema(description = "Date the payment was made (not in the future)", example = "2026-11-01")
        @NotNull(message = "Payment date is required")
        @PastOrPresent(message = "Payment date cannot be in the future")
        LocalDate paidOn,

        @Schema(description = "Optional note for the admin")
        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}
