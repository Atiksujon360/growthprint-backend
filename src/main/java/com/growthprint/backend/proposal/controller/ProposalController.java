package com.growthprint.backend.proposal.controller;

import com.growthprint.backend.proposal.dto.*;
import com.growthprint.backend.proposal.service.ProposalService;
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
@RequestMapping("/api/v1/proposals")
@Tag(name = "Proposals")
@SecurityRequirement(name = "bearerAuth")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @GetMapping
    @Operation(summary = "List all proposals")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProposalResponse> list() {
        return proposalService.findAll();
    }

    @GetMapping("/my")
    @Operation(summary = "List proposals for authenticated client")
    @PreAuthorize("hasRole('CLIENT')")
    public List<ProposalResponse> listMyProposals(@AuthenticationPrincipal String userId) {
        return proposalService.findMyProposals(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get proposal by ID")
    @PreAuthorize("hasRole('ADMIN')")
    public ProposalResponse getById(@PathVariable UUID id) {
        return proposalService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a new proposal")
    @PreAuthorize("hasRole('ADMIN')")
    public ProposalResponse create(@Valid @RequestBody CreateProposalCommand command) {
        return proposalService.create(command);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft proposal")
    @PreAuthorize("hasRole('ADMIN')")
    public ProposalResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProposalCommand command) {
        return proposalService.update(id, command);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a draft proposal for client review")
    @PreAuthorize("hasRole('ADMIN')")
    public ProposalResponse publish(@PathVariable UUID id, @Valid @RequestBody PublishProposalCommand command) {
        return proposalService.publish(id, command);
    }

    @PostMapping("/my/{id}/decision")
    @Operation(summary = "Record client decision (accept or decline) on a proposal")
    @PreAuthorize("hasRole('CLIENT')")
    public ProposalResponse recordDecision(
            @PathVariable UUID id,
            @Valid @RequestBody ProposalDecisionCommand command,
            @AuthenticationPrincipal String userId) {
        return proposalService.recordDecision(id, command, userId);
    }
}
