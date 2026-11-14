package com.growthprint.backend.auth.controller;

import com.growthprint.backend.auth.dto.AccountDtos.AcceptInvitationRequest;
import com.growthprint.backend.auth.dto.AccountDtos.ForgotPasswordRequest;
import com.growthprint.backend.auth.dto.AccountDtos.PasswordPolicyResponse;
import com.growthprint.backend.auth.dto.AccountDtos.ResetPasswordRequest;
import com.growthprint.backend.auth.dto.AccountDtos.TokenRequest;
import com.growthprint.backend.auth.dto.LoginResponse;
import com.growthprint.backend.auth.service.AccountService;
import com.growthprint.backend.auth.service.InvitationService;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InvitationPreviewResponse;
import com.growthprint.backend.auth.service.PasswordRecoveryService;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Public (unauthenticated) account recovery and invitation endpoints. All are rate limited per IP in
 * RateLimitFilter. Tokens travel in JSON bodies, never in URLs, so they do not end up in access logs.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Account recovery")
public class AccountRecoveryController {

    static final String GENERIC_RESET_MESSAGE =
            "If an account exists for that email, we've sent a link to reset the password.";

    private final PasswordRecoveryService passwordRecoveryService;
    private final InvitationService invitationService;
    private final AccountService accountService;

    public AccountRecoveryController(PasswordRecoveryService passwordRecoveryService,
                                     InvitationService invitationService, AccountService accountService) {
        this.passwordRecoveryService = passwordRecoveryService;
        this.invitationService = invitationService;
        this.accountService = accountService;
    }

    @GetMapping("/password/policy")
    @Operation(summary = "Password requirements for new passwords")
    public PasswordPolicyResponse passwordPolicy() {
        return PasswordPolicyResponse.current();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Request a password reset email",
            description = "Always 202 with the same message, whether or not the email belongs to an account.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Request accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid email",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordRecoveryService.requestReset(request.email());
        return ResponseEntity.accepted().body(Map.of("message", GENERIC_RESET_MESSAGE));
    }

    @PostMapping("/password/reset/verify")
    @Operation(summary = "Check whether a reset link is still valid")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Link is valid"),
            @ApiResponse(responseCode = "400", description = "Link invalid, expired or used",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> verifyResetToken(@Valid @RequestBody TokenRequest request) {
        passwordRecoveryService.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password with a reset link (signs out every existing session)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Link invalid/expired/used or password too weak",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordRecoveryService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/preview")
    @Operation(summary = "Show who an invitation is for (email, name, role, client) if the link is still valid")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation is valid"),
            @ApiResponse(responseCode = "400", description = "Link invalid, expired or used",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InvitationPreviewResponse previewInvitation(@Valid @RequestBody TokenRequest request) {
        return invitationService.preview(request.token());
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept an invitation by choosing a password; the account becomes active")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account activated and signed in"),
            @ApiResponse(responseCode = "400", description = "Link invalid/expired/used or password too weak",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LoginResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        UUID userId = invitationService.accept(request.token(), request.password());
        return accountService.startSession(userId);
    }
}
