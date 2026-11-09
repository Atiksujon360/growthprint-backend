package com.growthprint.backend.useradmin.controller;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InvitationResponse;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InviteUserRequest;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.ManagedUserResponse;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.OneTimeLink;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.SecurityOverviewResponse;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.UpdatePermissionsRequest;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.UpdateRoleRequest;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.UpdateStatusRequest;
import com.growthprint.backend.useradmin.service.UserAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * User, staff and permission management. ADMIN only (SecurityConfig + method security). Team members and
 * clients can never reach these endpoints, so nobody can elevate their own privileges.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - users and security")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdministrationService service;

    public AdminUserController(UserAdministrationService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @Operation(summary = "List users (admins, team members, client users); optional role and client filters")
    public List<ManagedUserResponse> list(@RequestParam(required = false) UserRole role,
                                          @RequestParam(required = false) UUID clientId) {
        return service.listUsers(role, clientId);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get one user")
    public ManagedUserResponse get(@PathVariable UUID id) {
        return service.getUser(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite a user; returns a one-time setup link (shown once, only its hash is stored)")
    public InvitationResponse invite(@AuthenticationPrincipal String adminId,
                                     @Valid @RequestBody InviteUserRequest request) {
        return service.invite(UUID.fromString(adminId), request);
    }

    @PostMapping("/users/{id}/invitation")
    @Operation(summary = "Issue a new setup link for a user who has not accepted yet (older links stop working)")
    public OneTimeLink reissueInvitation(@AuthenticationPrincipal String adminId, @PathVariable UUID id) {
        return service.reissueInvitation(UUID.fromString(adminId), id);
    }

    @PostMapping("/users/{id}/password-reset-link")
    @Operation(summary = "Issue a one-time password reset link for an active user")
    public OneTimeLink passwordResetLink(@AuthenticationPrincipal String adminId, @PathVariable UUID id) {
        return service.issuePasswordResetLink(UUID.fromString(adminId), id);
    }

    @PostMapping("/users/{id}/suspend")
    @Operation(summary = "Suspend a user (revokes their sessions immediately)")
    public ManagedUserResponse suspend(@AuthenticationPrincipal String adminId, @PathVariable UUID id) {
        return service.changeStatus(UUID.fromString(adminId), id, AccountStatus.SUSPENDED);
    }

    @PostMapping("/users/{id}/reactivate")
    @Operation(summary = "Re-activate a suspended or disabled user")
    public ManagedUserResponse reactivate(@AuthenticationPrincipal String adminId, @PathVariable UUID id) {
        return service.changeStatus(UUID.fromString(adminId), id, AccountStatus.ACTIVE);
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Activate, suspend or disable a user (revokes their sessions)")
    public ManagedUserResponse changeStatus(@AuthenticationPrincipal String adminId, @PathVariable UUID id,
                                            @Valid @RequestBody UpdateStatusRequest request) {
        return service.changeStatus(UUID.fromString(adminId), id, request.status());
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Switch a staff account between ADMIN and TEAM_MEMBER (revokes their sessions)")
    public ManagedUserResponse changeRole(@AuthenticationPrincipal String adminId, @PathVariable UUID id,
                                          @Valid @RequestBody UpdateRoleRequest request) {
        return service.changeRole(UUID.fromString(adminId), id, request.role());
    }

    @PutMapping("/users/{id}/permissions")
    @Operation(summary = "Replace a team member's module permissions (effective on their next request)")
    public ManagedUserResponse replacePermissions(@AuthenticationPrincipal String adminId, @PathVariable UUID id,
                                                  @Valid @RequestBody UpdatePermissionsRequest request) {
        return service.replacePermissions(UUID.fromString(adminId), id, request.modules());
    }

    @GetMapping("/security/overview")
    @Operation(summary = "Read-only security configuration (token lifetimes, email delivery, password policy)")
    public SecurityOverviewResponse securityOverview() {
        return service.securityOverview();
    }
}
