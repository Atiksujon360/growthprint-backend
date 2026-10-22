package com.growthprint.backend.inquiry.controller;

import com.growthprint.backend.inquiry.dto.InquiryAcceptedResponse;
import com.growthprint.backend.inquiry.dto.PublicInquiryRequest;
import com.growthprint.backend.lead.dto.CreateLeadCommand;
import com.growthprint.backend.lead.dto.LeadResponse;
import com.growthprint.backend.lead.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/inquiries")
@Tag(name = "Public inquiries")
public class PublicInquiryController {

    private final LeadService leadService;

    public PublicInquiryController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @Operation(summary = "Submit a project inquiry")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inquiry received"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.growthprint.backend.common.dto.ApiErrorResponse.class)))
    })
    public ResponseEntity<InquiryAcceptedResponse> submit(@Valid @RequestBody PublicInquiryRequest request) {
        CreateLeadCommand command = new CreateLeadCommand(
                request.name(),
                request.businessEmail(),
                request.company(),
                request.website(),
                request.serviceCategory(),
                request.projectType(),
                request.budgetRange(),
                request.preferredTimeline(),
                request.projectDescription(),
                request.preferredContactMethod(),
                request.consent()
        );

        LeadResponse lead = leadService.createFromInquiry(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InquiryAcceptedResponse(lead.referenceNumber(), lead.status(), lead.receivedAt()));
    }
}
