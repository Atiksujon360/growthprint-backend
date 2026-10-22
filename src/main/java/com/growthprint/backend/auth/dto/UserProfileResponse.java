package com.growthprint.backend.auth.dto;

import java.time.Instant;

public record UserProfileResponse(
        String id,
        String email,
        String role,
        Instant createdAt
) {
}
