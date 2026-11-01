package com.growthprint.backend.auth.controller;

import com.growthprint.backend.auth.dto.AccountDtos.ChangePasswordRequest;
import com.growthprint.backend.auth.dto.AccountDtos.UpdateProfileRequest;
import com.growthprint.backend.auth.dto.LoginResponse;
import com.growthprint.backend.auth.dto.UserProfileResponse;
import com.growthprint.backend.auth.service.AccountService;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Self-service account settings for every authenticated role. The account is always the caller's own (from
 * the token); there is no user id in the path, so one user can never address another user's account here.
 * Role, status, email and client membership cannot be changed through these endpoints.
 */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account settings")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/profile")
    @Operation(summary = "My profile, preferences and security details")
    public UserProfileResponse profile(@AuthenticationPrincipal String userId) {
        return accountService.profile(UUID.fromString(userId));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update my name, phone, job title, time zone and notification preference")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserProfileResponse updateProfile(@AuthenticationPrincipal String userId,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(UUID.fromString(userId), request);
    }

    @PostMapping("/password")
    @Operation(summary = "Change my password",
            description = "Signs out every other session and returns a fresh token for this device.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed; use the returned token"),
            @ApiResponse(responseCode = "400", description = "Wrong current password or weak new password",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LoginResponse changePassword(@AuthenticationPrincipal String userId,
                                        @Valid @RequestBody ChangePasswordRequest request) {
        return accountService.changePassword(UUID.fromString(userId), request.currentPassword(),
                request.newPassword());
    }

    @PostMapping("/sessions/revoke")
    @Operation(summary = "Sign out everywhere (all devices, including this one)")
    public ResponseEntity<Void> revokeSessions(@AuthenticationPrincipal String userId) {
        accountService.revokeAllSessions(UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
