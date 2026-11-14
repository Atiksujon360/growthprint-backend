package com.growthprint.backend.business.dto;

import com.growthprint.backend.business.entity.BusinessRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request and response bodies for /api/v1/businesses and /api/v1/business-invitations. */
public final class BusinessDtos {

    private BusinessDtos() {
    }

    /** A business as seen by one of its members, including the caller's own role. */
    public record BusinessResponse(UUID id, String name, String slug, String status, String myRole, Instant createdAt) {
    }

    public record CreateBusinessRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 160, message = "Name must be at most 160 characters")
            String name,
            /** Optional; derived from the name when absent. */
            @Pattern(regexp = "^$|^[a-z0-9]([a-z0-9-]{0,78}[a-z0-9])?$",
                    message = "Use lowercase letters, numbers and hyphens (2–80 characters)")
            String slug) {
    }

    public record UpdateBusinessRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 160, message = "Name must be at most 160 characters")
            String name) {
    }

    public record MemberResponse(UUID userId, String email, String fullName, String role, Instant joinedAt) {
    }

    public record ChangeRoleRequest(@NotNull(message = "Role is required") BusinessRole role) {
    }

    public record TransferOwnershipRequest(@NotNull(message = "New owner is required") UUID userId) {
    }

    public record InviteMemberRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 254, message = "Email is too long")
            String email,
            @NotNull(message = "Role is required")
            BusinessRole role) {
    }

    public record InvitationResponse(UUID id, String email, String role, String status, Instant expiresAt,
                                     Instant createdAt) {
    }

    /** Returned once to the inviter; only the token's hash is stored. */
    public record CreatedInvitationResponse(InvitationResponse invitation, String token, String link) {
    }

    public record AcceptInvitationRequest(
            @NotBlank(message = "Token is required")
            @Size(max = 128, message = "Token is invalid")
            String token) {
    }
}
