package com.growthprint.backend.useradmin.dto;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.ClientRole;
import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** DTOs for admin user, staff and permission management (/api/v1/admin/users). */
public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record ManagedUserResponse(
            String id,
            String email,
            String fullName,
            String role,
            String accountStatus,
            String clientId,
            String clientRole,
            List<String> permissions,
            Instant lastLoginAt,
            Instant passwordChangedAt,
            Instant createdAt) {

        /** Must be called inside a transaction (permissions are lazily loaded). */
        public static ManagedUserResponse from(User user) {
            return new ManagedUserResponse(
                    user.getId().toString(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole().name(),
                    user.getAccountStatus().name(),
                    user.getClientId() == null ? null : user.getClientId().toString(),
                    user.getClientRole() == null ? null : user.getClientRole().name(),
                    user.getPermissions().stream().map(Enum::name).sorted().toList(),
                    user.getLastLoginAt(),
                    user.getPasswordChangedAt(),
                    user.getCreatedAt());
        }
    }

    public record InviteUserRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 254, message = "Email is too long")
            String email,
            @NotBlank(message = "Name is required")
            @Size(max = 160, message = "Name must be at most 160 characters")
            String fullName,
            @NotNull(message = "Role is required")
            UserRole role,
            /** Required for CLIENT users: the client company the login belongs to. */
            UUID clientId,
            /** CLIENT users only; defaults to CLIENT_MEMBER. */
            ClientRole clientRole,
            /** TEAM_MEMBER only. */
            Set<TeamModule> permissions) {
    }

    /** The setup link is returned exactly once, to the admin who created it, and is never stored in plain text. */
    public record OneTimeLinkResponse(ManagedUserResponse user, String link, Instant expiresAt) {
    }

    public record UpdateStatusRequest(@NotNull(message = "Status is required") AccountStatus status) {
    }

    public record UpdateRoleRequest(@NotNull(message = "Role is required") UserRole role) {
    }

    public record UpdatePermissionsRequest(@NotNull(message = "Permissions are required") Set<TeamModule> modules) {
    }

    /** Read-only security configuration for the admin security settings screen. */
    public record SecurityOverviewResponse(
            boolean emailDeliveryConfigured,
            long accessTokenMinutes,
            long passwordResetMinutes,
            long invitationHours,
            int passwordMinLength,
            String passwordPolicy,
            List<String> grantableModules,
            long activeAdmins) {
    }
}
