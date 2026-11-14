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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm-leads")
@Tag(name = "CRM Leads")
public class CrmLeadController {

    private final CrmLeadService crmLeadService;
    private final com.growthprint.backend.crm.lead.service.LeadConversionService leadConversionService;

    public CrmLeadController(CrmLeadService crmLeadService,
                             com.growthprint.backend.crm.lead.service.LeadConversionService leadConversionService) {
        this.crmLeadService = crmLeadService;
        this.leadConversionService = leadConversionService;
    }

    @GetMapping
    @Operation(summary = "List all CRM leads", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CRM')")
    public List<CrmLeadResponse> list() {
        return crmLeadService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get CRM lead by ID", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CRM')")
    public CrmLeadResponse getById(@PathVariable UUID id) {
        return crmLeadService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a new CRM lead", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CRM')")
    public CrmLeadResponse create(@Valid @RequestBody CreateCrmLeadCommand command) {
        return crmLeadService.create(command);
    }

    @PostMapping("/inquiry/{inquiryId}/convert")
    @Operation(summary = "Convert an inquiry to a CRM lead", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CRM')")
    public CrmLeadResponse convertFromInquiry(
            @PathVariable UUID inquiryId,
            @Valid @RequestBody CreateCrmLeadCommand command) {
        return crmLeadService.convertFromInquiry(inquiryId, command);
    }

    @PutMapping("/{id}/pipeline-stage")
    @Operation(summary = "Update CRM lead pipeline stage", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CRM')")
    public CrmLeadResponse updatePipelineStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePipelineStageCommand command) {
        return crmLeadService.updatePipelineStage(id, command);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a CRM lead", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CRM')")
    public void delete(@PathVariable UUID id) {
        crmLeadService.delete(id);
    }

    @PostMapping("/{id}/convert-to-client")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.Operation(summary = "Convert a lead into a client (once, transactional)",
            security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('PERM_CRM') and hasAuthority('PERM_CLIENTS'))")
    public com.growthprint.backend.client.dto.ClientResponse convertToClient(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody(required = false)
            com.growthprint.backend.crm.lead.dto.ConvertLeadToClientCommand command) {
        return leadConversionService.convert(id, command, UUID.fromString(userId));
    }
}
