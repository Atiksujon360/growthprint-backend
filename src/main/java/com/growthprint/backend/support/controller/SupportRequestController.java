package com.growthprint.backend.support.controller;

import com.growthprint.backend.support.dto.*;
import com.growthprint.backend.support.service.SupportRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support-requests")
@Tag(name = "Support Requests")
@SecurityRequirement(name = "bearerAuth")
public class SupportRequestController {

    private final SupportRequestService supportRequestService;

    public SupportRequestController(SupportRequestService supportRequestService) {
        this.supportRequestService = supportRequestService;
    }

    @GetMapping
    @Operation(summary = "List all support requests")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SupportRequestResponse> list() {
        return supportRequestService.findAll();
    }

    @GetMapping("/my")
    @Operation(summary = "List support requests for authenticated client")
    @PreAuthorize("hasRole('CLIENT')")
    public List<SupportRequestResponse> listMyRequests(@AuthenticationPrincipal String userId) {
        return supportRequestService.findMyRequests(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get support request by ID")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportRequestResponse getById(@PathVariable UUID id) {
        return supportRequestService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a support request")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportRequestResponse create(@Valid @RequestBody CreateSupportRequestCommand command) {
        return supportRequestService.create(command);
    }

    @PostMapping("/my")
    @Operation(summary = "Create a support request for authenticated client")
    @PreAuthorize("hasRole('CLIENT')")
    public SupportRequestResponse createMyRequest(
            @Valid @RequestBody CreateMySupportRequestCommand command,
            @AuthenticationPrincipal String userId) {
        return supportRequestService.createMyRequest(command, userId);
    }

    @PostMapping("/{id}/classify")
    @Operation(summary = "Classify a support request",
            description = "Only allowed while the request is NEW or NEEDS_CLASSIFICATION; otherwise 400.")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportRequestResponse classify(@PathVariable UUID id, @Valid @RequestBody ClassifyRequestCommand command) {
        return supportRequestService.classify(id, command);
    }

    @PostMapping("/{id}/estimate")
    @Operation(summary = "Attach an estimate to a support request")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportRequestResponse attachEstimate(@PathVariable UUID id, @Valid @RequestBody AttachEstimateCommand command) {
        return supportRequestService.attachEstimate(id, command);
    }

    @PostMapping("/{id}/approve-estimate")
    @Operation(summary = "Approve an estimate for a support request")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportRequestResponse approveEstimate(@PathVariable UUID id, @Valid @RequestBody ApproveEstimateCommand command) {
        return supportRequestService.approveEstimate(id, command);
    }

    @PostMapping("/my/{id}/approve-estimate")
    @Operation(summary = "Approve an estimate for authenticated client's support request",
            description = "The approval is recorded against the authenticated user's email; the approvedBy "
                    + "field is accepted for backwards compatibility but ignored. Another client's request returns 404.")
    @PreAuthorize("hasRole('CLIENT')")
    public SupportRequestResponse approveMyEstimate(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveEstimateCommand command,
            @AuthenticationPrincipal String userId) {
        return supportRequestService.approveMyEstimate(id, command, userId);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update support request status")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportRequestResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateRequestStatusCommand command) {
        return supportRequestService.updateStatus(id, command);
    }
}
