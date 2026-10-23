package com.growthprint.backend.support.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AttachEstimateCommand(
        @NotNull(message = "Estimated hours is required")
        @DecimalMin(value = "0.01", message = "Estimated hours must be positive")
        BigDecimal estimatedHours,

        @NotNull(message = "Hourly rate is required")
        @DecimalMin(value = "0.00", message = "Hourly rate must be non-negative")
        BigDecimal hourlyRate,

        @NotNull(message = "Delivery timeline days is required")
        @Min(value = 1, message = "Delivery timeline days must be at least 1")
        Integer deliveryTimelineDays,

        @NotBlank(message = "Explanation is required")
        String explanation
) {
}
