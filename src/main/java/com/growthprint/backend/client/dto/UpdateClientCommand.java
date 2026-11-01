package com.growthprint.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update for PATCH /api/v1/clients/{id}. Omitted (or null) fields are left unchanged.
 * <p>
 * Replaces the former raw {@code Map<String, Object>} body: the frontend sends {@code phone} (the field name
 * in ClientResponse) but the backend only read {@code contactPhone}, so phone updates were silently dropped,
 * and non-string values caused a 500. Both spellings are now accepted. An empty {@code phone} clears it.
 */
@Schema(description = "Partial client update; omitted fields are unchanged")
public record UpdateClientCommand(
        @Size(max = 120, message = "Name must not exceed 120 characters")
        @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
        String name,

        @Email(message = "Contact email must be valid")
        @Size(max = 254, message = "Contact email must not exceed 254 characters")
        @Pattern(regexp = ".*\\S.*", message = "Contact email must not be blank")
        String contactEmail,

        @JsonAlias("contactPhone")
        @Size(max = 50, message = "Phone must not exceed 50 characters")
        String phone,

        @Size(max = 10000, message = "Notes must not exceed 10000 characters")
        String notes
) {
}
