package com.growthprint.backend.business.controller;

import com.growthprint.backend.business.dto.BusinessDtos.AcceptInvitationRequest;
import com.growthprint.backend.business.dto.BusinessDtos.BusinessResponse;
import com.growthprint.backend.business.dto.BusinessDtos.ChangeRoleRequest;
import com.growthprint.backend.business.dto.BusinessDtos.CreateBusinessRequest;
import com.growthprint.backend.business.dto.BusinessDtos.CreatedInvitationResponse;
import com.growthprint.backend.business.dto.BusinessDtos.InvitationResponse;
import com.growthprint.backend.business.dto.BusinessDtos.InviteMemberRequest;
import com.growthprint.backend.business.dto.BusinessDtos.MemberResponse;
import com.growthprint.backend.business.dto.BusinessDtos.TransferOwnershipRequest;
import com.growthprint.backend.business.dto.BusinessDtos.UpdateBusinessRequest;
import com.growthprint.backend.business.service.BusinessMembershipService;
import com.growthprint.backend.business.service.BusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Businesses (tenants), memberships and invitations. Every route requires authentication; access to a specific
 * business is decided server-side from the caller's membership (BusinessAccessService), never from the path alone.
 * Non-members receive 404 for any business id.
 */
@RestController
@Tag(name = "Businesses and members")
@SecurityRequirement(name = "bearerAuth")
public class BusinessController {

    private final BusinessService businessService;
    private final BusinessMembershipService membershipService;

    public BusinessController(BusinessService businessService, BusinessMembershipService membershipService) {
        this.businessService = businessService;
        this.membershipService = membershipService;
    }

    @GetMapping("/api/v1/businesses")
    @Operation(summary = "Businesses I belong to, with my role in each")
    public List<BusinessResponse> myBusinesses(@AuthenticationPrincipal String userId) {
        return businessService.listMine(UUID.fromString(userId));
    }

    @PostMapping("/api/v1/businesses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a business; the creator becomes its owner")
    public BusinessResponse create(@AuthenticationPrincipal String userId,
                                   @Valid @RequestBody CreateBusinessRequest request) {
        return businessService.create(UUID.fromString(userId), request);
    }

    @GetMapping("/api/v1/businesses/{businessId}")
    @Operation(summary = "Get a business I belong to")
    public BusinessResponse get(@AuthenticationPrincipal String userId, @PathVariable UUID businessId) {
        return businessService.get(businessId, UUID.fromString(userId));
    }

    @PatchMapping("/api/v1/businesses/{businessId}")
    @Operation(summary = "Rename a business (ADMIN or OWNER)")
    public BusinessResponse update(@AuthenticationPrincipal String userId, @PathVariable UUID businessId,
                                   @Valid @RequestBody UpdateBusinessRequest request) {
        return businessService.rename(businessId, UUID.fromString(userId), request.name());
    }

    @GetMapping("/api/v1/businesses/{businessId}/members")
    @Operation(summary = "List active members")
    public List<MemberResponse> members(@AuthenticationPrincipal String userId, @PathVariable UUID businessId) {
        return membershipService.listMembers(businessId, UUID.fromString(userId));
    }

    @PatchMapping("/api/v1/businesses/{businessId}/members/{memberId}")
    @Operation(summary = "Change a member's role (owner role changes need an OWNER)")
    public MemberResponse changeRole(@AuthenticationPrincipal String userId, @PathVariable UUID businessId,
                                     @PathVariable UUID memberId, @Valid @RequestBody ChangeRoleRequest request) {
        return membershipService.changeRole(businessId, UUID.fromString(userId), memberId, request.role());
    }

    @DeleteMapping("/api/v1/businesses/{businessId}/members/{memberId}")
    @Operation(summary = "Remove a member, or leave the business when memberId is yourself")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal String userId, @PathVariable UUID businessId,
                                             @PathVariable UUID memberId) {
        membershipService.removeMember(businessId, UUID.fromString(userId), memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/businesses/{businessId}/ownership-transfer")
    @Operation(summary = "Transfer ownership to another member (current OWNER only)")
    public MemberResponse transferOwnership(@AuthenticationPrincipal String userId, @PathVariable UUID businessId,
                                            @Valid @RequestBody TransferOwnershipRequest request) {
        return membershipService.transferOwnership(businessId, UUID.fromString(userId), request.userId());
    }

    @GetMapping("/api/v1/businesses/{businessId}/invitations")
    @Operation(summary = "Pending invitations (ADMIN or OWNER)")
    public List<InvitationResponse> invitations(@AuthenticationPrincipal String userId,
                                                @PathVariable UUID businessId) {
        return membershipService.listPendingInvitations(businessId, UUID.fromString(userId));
    }

    @PostMapping("/api/v1/businesses/{businessId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite someone by email; returns a one-time link (only its hash is stored)")
    public CreatedInvitationResponse invite(@AuthenticationPrincipal String userId, @PathVariable UUID businessId,
                                            @Valid @RequestBody InviteMemberRequest request) {
        return membershipService.invite(businessId, UUID.fromString(userId), request.email(), request.role());
    }

    @DeleteMapping("/api/v1/businesses/{businessId}/invitations/{invitationId}")
    @Operation(summary = "Revoke a pending invitation")
    public ResponseEntity<Void> revokeInvitation(@AuthenticationPrincipal String userId,
                                                 @PathVariable UUID businessId, @PathVariable UUID invitationId) {
        membershipService.revokeInvitation(businessId, UUID.fromString(userId), invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/business-invitations/accept")
    @Operation(summary = "Join a business with an invitation addressed to my email")
    public BusinessResponse acceptInvitation(@AuthenticationPrincipal String userId,
                                             @Valid @RequestBody AcceptInvitationRequest request) {
        return membershipService.acceptInvitation(UUID.fromString(userId), request.token());
    }
}
