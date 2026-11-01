package com.growthprint.backend.invoice.controller;

import com.growthprint.backend.common.dto.ApiErrorResponse;
import com.growthprint.backend.invoice.dto.PaymentReportResponse;
import com.growthprint.backend.invoice.dto.ReportPaymentCommand;
import com.growthprint.backend.invoice.dto.ReviewPaymentReportCommand;
import com.growthprint.backend.invoice.service.InvoicePaymentReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Client payment reporting and admin verification. A CLIENT can never mark an invoice PAID: reporting only
 * creates a PENDING report; ADMIN verification marks the invoice PAID.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoice payment reports")
@SecurityRequirement(name = "bearerAuth")
public class InvoicePaymentReportController {

    private final InvoicePaymentReportService paymentReportService;

    public InvoicePaymentReportController(InvoicePaymentReportService paymentReportService) {
        this.paymentReportService = paymentReportService;
    }

    @PostMapping("/my/{id}/reconcile")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a payment for one of my invoices (CLIENT)",
            description = "Creates a PENDING payment report for admin verification. The invoice status does NOT "
                    + "change. Only ISSUED or OVERDUE invoices accept reports; at most one report can be pending.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Report created (status PENDING)"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invoice not found (or not yours)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invoice not ISSUED/OVERDUE, or a report is already pending",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CLIENT')")
    public PaymentReportResponse reportPayment(@PathVariable UUID id,
                                               @Valid @RequestBody ReportPaymentCommand command,
                                               @AuthenticationPrincipal String userId) {
        return paymentReportService.reportPayment(id, command, userId);
    }

    @GetMapping("/my/{id}/payment-reports")
    @Operation(summary = "List payment reports for one of my invoices (CLIENT), newest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reports"),
            @ApiResponse(responseCode = "404", description = "Invoice not found (or not yours)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("hasRole('CLIENT')")
    public List<PaymentReportResponse> listMyReports(@PathVariable UUID id, @AuthenticationPrincipal String userId) {
        return paymentReportService.findMyReports(id, userId);
    }

    @GetMapping("/{id}/payment-reports")
    @Operation(summary = "List payment reports for an invoice (ADMIN), newest first")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentReportResponse> listReports(@PathVariable UUID id) {
        return paymentReportService.findReports(id);
    }

    @PostMapping("/payment-reports/{reportId}/verify")
    @Operation(summary = "Verify a pending payment report (ADMIN); marks the invoice PAID",
            description = "Body optional: {\"note\": \"...\"}. 409 if the report was already reviewed; "
                    + "400 if the invoice is VOID.")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentReportResponse verify(@PathVariable UUID reportId,
                                        @Valid @RequestBody(required = false) ReviewPaymentReportCommand command,
                                        @AuthenticationPrincipal String adminUserId) {
        return paymentReportService.verify(reportId, command, adminUserId);
    }

    @PostMapping("/payment-reports/{reportId}/reject")
    @Operation(summary = "Reject a pending payment report (ADMIN); the invoice is unchanged",
            description = "Body required: {\"note\": \"reason shown to the client\"}. 409 if already reviewed.")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentReportResponse reject(@PathVariable UUID reportId,
                                        @Valid @RequestBody(required = false) ReviewPaymentReportCommand command,
                                        @AuthenticationPrincipal String adminUserId) {
        return paymentReportService.reject(reportId, command, adminUserId);
    }
}
