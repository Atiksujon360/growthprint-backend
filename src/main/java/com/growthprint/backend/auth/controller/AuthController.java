package com.growthprint.backend.auth.controller;

import com.growthprint.backend.auth.dto.LoginRequest;
import com.growthprint.backend.auth.dto.LoginResponse;
import com.growthprint.backend.auth.dto.UserProfileResponse;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and obtain access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = com.growthprint.backend.common.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.growthprint.backend.common.dto.ApiErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.authenticate(request.email(), request.password());
        String accessToken = jwtTokenProvider.generateToken(user);

        Instant expiresAt = Instant.now().plus(jwtProperties.getExpiration());

        LoginResponse.UserProfile userProfile = new LoginResponse.UserProfile(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name()
        );

        LoginResponse response = new LoginResponse(
                accessToken,
                "Bearer",
                expiresAt,
                userProfile
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.growthprint.backend.common.dto.ApiErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal String userId) {
        User user = authService.getUserById(UUID.fromString(userId));

        UserProfileResponse response = new UserProfileResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout (client-side token discard; JWTs are stateless)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful (client should discard token)")
    })
    public ResponseEntity<Void> logout() {
        // JWTs are stateless; logout is handled client-side by discarding the token
        // This endpoint exists for API contract completeness and future server-side revocation
        return ResponseEntity.ok().build();
    }
}
