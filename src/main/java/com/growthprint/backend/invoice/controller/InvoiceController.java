package com.growthprint.backend.invoice.controller;

import com.growthprint.backend.invoice.dto.CreateInvoiceCommand;
import com.growthprint.backend.invoice.dto.InvoiceResponse;
import com.growthprint.backend.invoice.dto.MarkInvoicePaidCommand;
import com.growthprint.backend.invoice.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @Operation(summary = "List all invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public List<InvoiceResponse> list() {
        return invoiceService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice by ID")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceResponse getById(@PathVariable UUID id) {
        return invoiceService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a new invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceCommand command) {
        return invoiceService.create(command);
    }

    @PostMapping("/{id}/reconcile")
    @Operation(summary = "Mark invoice as paid")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceResponse markPaid(
            @PathVariable UUID id,
            @Valid @RequestBody MarkInvoicePaidCommand command) {
        return invoiceService.markPaid(id, command);
    }
}
