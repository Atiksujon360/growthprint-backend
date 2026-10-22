package com.growthprint.backend.lead.controller;

import com.growthprint.backend.lead.dto.LeadResponse;
import com.growthprint.backend.lead.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leads")
@ConditionalOnProperty(name = "app.admin-endpoints.enabled", havingValue = "true")
@Tag(name = "Development admin leads")
public class AdminLeadController {

    private final LeadService leadService;

    public AdminLeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    @Operation(summary = "List leads (development-only; no authentication)")
    public List<LeadResponse> list() {
        return leadService.findAll();
    }
}
