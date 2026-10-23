package com.growthprint.backend.support.dto;

import jakarta.validation.constraints.NotBlank;

public record ClassifyRequestCommand(
        @NotBlank(message = "Category is required")
        String category,

        String adminNotes
) {
}
