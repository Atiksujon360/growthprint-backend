package com.growthprint.backend.auth.dto;

import com.growthprint.backend.auth.entity.User;

import java.time.Instant;
import java.util.List;

/**
 * Current user (GET /api/v1/auth/me and GET /api/v1/account/profile).
 * The first four members are the original contract; the rest were added later (additive, non-breaking).
 */
public record UserProfileResponse(
        String id,
        String email,
        String role,
        Instant createdAt,
        String fullName,
        String accountStatus,
        String clientId,
        String clientRole,
        String phone,
        String jobTitle,
        String timezone,
        boolean emailNotifications,
        List<String> permissions,
        Instant lastLoginAt,
        Instant passwordChangedAt
) {

    /** Must be called inside a transaction (permissions are lazily loaded). */
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getFullName(),
                user.getAccountStatus().name(),
                user.getClientId() == null ? null : user.getClientId().toString(),
                user.getClientRole() == null ? null : user.getClientRole().name(),
                user.getPhone(),
                user.getJobTitle(),
                user.getTimezone(),
                user.isEmailNotifications(),
                user.getPermissions().stream().map(Enum::name).sorted().toList(),
                user.getLastLoginAt(),
                user.getPasswordChangedAt());
    }
}
