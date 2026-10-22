package com.growthprint.backend.auth.dto;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfile user
) {
    public record UserProfile(
            String id,
            String email,
            String role
    ) {
    }
}
