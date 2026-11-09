package com.growthprint.backend.auth.dto;

import com.growthprint.backend.auth.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies for password recovery, invitations and self-service account settings. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 254, message = "Email is too long")
            String email) {
    }

    public record TokenRequest(
            @NotBlank(message = "Token is required")
            @Size(max = 128, message = "Token is invalid")
            String token) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Token is required")
            @Size(max = 128, message = "Token is invalid")
            String token,
            @NotBlank(message = "New password is required")
            @Size(max = PasswordPolicy.MAX_LENGTH, message = "Password is too long")
            String newPassword) {
    }

    public record AcceptInvitationRequest(
            @NotBlank(message = "Token is required")
            @Size(max = 128, message = "Token is invalid")
            String token,
            @NotBlank(message = "Password is required")
            @Size(max = PasswordPolicy.MAX_LENGTH, message = "Password is too long")
            String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            @Size(max = 256, message = "Current password is invalid")
            String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(max = PasswordPolicy.MAX_LENGTH, message = "Password is too long")
            String newPassword) {
    }

    /** Self-service profile. Email, role, status and client membership are deliberately NOT editable here. */
    public record UpdateProfileRequest(
            @Size(max = 160, message = "Name must be at most 160 characters")
            String fullName,
            @Size(max = 40, message = "Phone must be at most 40 characters")
            @Pattern(regexp = "^$|^[+0-9 ()\\-.]{4,40}$", message = "Phone may contain digits, spaces and + ( ) - .")
            String phone,
            @Size(max = 120, message = "Job title must be at most 120 characters")
            String jobTitle,
            @Size(max = 64, message = "Time zone is invalid")
            String timezone,
            Boolean emailNotifications) {
    }

    /** Password requirements for the frontend (single source of truth). */
    public record PasswordPolicyResponse(int minLength, int maxLength, String description) {
        public static PasswordPolicyResponse current() {
            return new PasswordPolicyResponse(PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH,
                    PasswordPolicy.DESCRIPTION);
        }
    }
}
