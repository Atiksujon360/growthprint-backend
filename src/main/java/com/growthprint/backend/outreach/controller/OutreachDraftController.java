package com.growthprint.backend.outreach.controller;

import com.growthprint.backend.outreach.dto.OutreachDraftResponse;
import com.growthprint.backend.outreach.service.OutreachDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outreach/drafts")
@Tag(name = "Outreach Drafts")
@SecurityRequirement(name = "bearerAuth")
public class OutreachDraftController {

    private final OutreachDraftService outreachDraftService;

    public OutreachDraftController(OutreachDraftService outreachDraftService) {
        this.outreachDraftService = outreachDraftService;
    }

    @GetMapping
    @Operation(summary = "List all outreach drafts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OutreachDraftResponse> list() {
        return outreachDraftService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get outreach draft by ID")
    @PreAuthorize("hasRole('ADMIN')")
    public OutreachDraftResponse getById(@PathVariable UUID id) {
        return outreachDraftService.findById(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an outreach draft")
    @PreAuthorize("hasRole('ADMIN')")
    public OutreachDraftResponse approve(@PathVariable UUID id) {
        return outreachDraftService.approve(id);
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send an approved outreach draft (record only, no actual email sent)")
    @PreAuthorize("hasRole('ADMIN')")
    public OutreachDraftResponse send(@PathVariable UUID id) {
        return outreachDraftService.send(id);
    }
}
