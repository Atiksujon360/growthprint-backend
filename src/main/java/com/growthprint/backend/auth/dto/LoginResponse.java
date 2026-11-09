package com.growthprint.backend.auth.dto;

import java.time.Instant;
import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfile user
) {
    /**
     * {@code id}, {@code email}, {@code role} are the original contract; {@code fullName} and
     * {@code permissions} were added later (additive).
     */
    public record UserProfile(
            String id,
            String email,
            String role,
            String fullName,
            List<String> permissions
    ) {
    }
}
