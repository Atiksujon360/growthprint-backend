package com.growthprint.backend.invoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Optional body for ADMIN verify/reject of a payment report. A note is required when rejecting
 * (the client should learn why), optional when verifying.
 */
@Schema(description = "Admin review note; required for reject")
public record ReviewPaymentReportCommand(
        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}
