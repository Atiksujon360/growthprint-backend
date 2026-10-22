package com.growthprint.backend.crm.lead.controller;

import com.growthprint.backend.crm.lead.dto.CreateCrmLeadCommand;
import com.growthprint.backend.crm.lead.dto.CrmLeadResponse;
import com.growthprint.backend.crm.lead.dto.UpdatePipelineStageCommand;
import com.growthprint.backend.crm.lead.service.CrmLeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm-leads")
@Tag(name = "CRM Leads")
public class CrmLeadController {

    private final CrmLeadService crmLeadService;

    public CrmLeadController(CrmLeadService crmLeadService) {
        this.crmLeadService = crmLeadService;
    }

    @GetMapping
    @Operation(summary = "List all CRM leads", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public List<CrmLeadResponse> list() {
        return crmLeadService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get CRM lead by ID", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public CrmLeadResponse getById(@PathVariable UUID id) {
        return crmLeadService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a new CRM lead", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public CrmLeadResponse create(@Valid @RequestBody CreateCrmLeadCommand command) {
        return crmLeadService.create(command);
    }

    @PostMapping("/inquiry/{inquiryId}/convert")
    @Operation(summary = "Convert an inquiry to a CRM lead", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public CrmLeadResponse convertFromInquiry(
            @PathVariable UUID inquiryId,
            @Valid @RequestBody CreateCrmLeadCommand command) {
        return crmLeadService.convertFromInquiry(inquiryId, command);
    }

    @PutMapping("/{id}/pipeline-stage")
    @Operation(summary = "Update CRM lead pipeline stage", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public CrmLeadResponse updatePipelineStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePipelineStageCommand command) {
        return crmLeadService.updatePipelineStage(id, command);
    }
}
