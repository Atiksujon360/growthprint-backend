package com.growthprint.backend.auth.controller;

import com.growthprint.backend.auth.dto.AccountDtos.ChangePasswordRequest;
import com.growthprint.backend.auth.dto.LoginRequest;
import com.growthprint.backend.auth.dto.LoginResponse;
import com.growthprint.backend.auth.dto.UserProfileResponse;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.exception.AuthenticationFailedException;
import com.growthprint.backend.auth.service.AccountService;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.auth.service.LoginThrottle;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import com.growthprint.backend.security.SecurityAudit;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;
    private final LoginThrottle loginThrottle;

    public AuthController(AuthService authService, AccountService accountService, LoginThrottle loginThrottle) {
        this.authService = authService;
        this.accountService = accountService;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and obtain access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many attempts (per IP or per account)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        loginThrottle.checkAllowed(request.email());
        User user;
        try {
            user = authService.authenticate(request.email(), request.password());
        } catch (AuthenticationFailedException e) {
            SecurityAudit.record(SecurityAudit.Event.LOGIN_FAILED, null, null, null);
            throw e;
        }
        return ResponseEntity.ok(accountService.startSession(user.getId()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(accountService.profile(UUID.fromString(userId)));
    }

    /**
     * Legacy-compatible password change (MVP1 contract): same rules as POST /api/v1/account/password, but
     * revokes EVERY session including this one and returns 204, so the caller signs in again.
     */
    @PostMapping("/password")
    @Operation(summary = "Change my password and sign out everywhere (204). Prefer POST /api/v1/account/password.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed; all sessions revoked"),
            @ApiResponse(responseCode = "400", description = "Wrong current password or weak new password",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal String userId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        UUID id = UUID.fromString(userId);
        accountService.changePassword(id, request.currentPassword(), request.newPassword());
        accountService.revokeAllSessions(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout on this device (client discards the token). Use POST /api/v1/account/sessions/revoke to sign out everywhere.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful (client should discard token)")
    })
    public ResponseEntity<Void> logout() {
        // Stateless JWT: this device discards its token. Server-side revocation of ALL sessions is
        // POST /api/v1/account/sessions/revoke (token_version bump).
        return ResponseEntity.ok().build();
    }
}
