package com.growthprint.backend.invoice.service;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.invoice.dto.CreateInvoiceCommand;
import com.growthprint.backend.invoice.dto.InvoiceLineItemResponse;
import com.growthprint.backend.invoice.dto.InvoiceResponse;
import com.growthprint.backend.invoice.dto.MarkInvoicePaidCommand;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoiceLineItem;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import com.growthprint.backend.invoice.exception.InvoiceAlreadyExistsException;
import com.growthprint.backend.invoice.exception.InvalidInvoiceStatusTransitionException;
import com.growthprint.backend.invoice.exception.InvoiceNotFoundException;
import com.growthprint.backend.invoice.repository.InvoiceLineItemRepository;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final ClientRepository clientRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceLineItemRepository lineItemRepository,
                          ClientRepository clientRepository) {
        this.invoiceRepository = invoiceRepository;
        this.lineItemRepository = lineItemRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findAll() {
        List<Invoice> invoices = invoiceRepository.findAll();

        // Batch fetch all clients to avoid N+1 queries
        List<UUID> clientIds = invoices.stream()
                .map(Invoice::getClientId)
                .distinct()
                .toList();

        Map<UUID, Client> clientMap = new HashMap<>();
        if (!clientIds.isEmpty()) {
            clientRepository.findAllById(clientIds).forEach(client ->
                    clientMap.put(client.getId(), client));
        }

        return invoices.stream()
                .map(invoice -> toResponseWithLineItems(invoice, clientMap))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));

        Client invoiceClient = clientRepository.findById(invoice.getClientId())
                .orElse(null);
        Map<UUID, Client> clientMap = new HashMap<>();
        if (invoiceClient != null) {
            clientMap.put(invoice.getClientId(), invoiceClient);
        }

        return toResponseWithLineItems(invoice, clientMap);
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceCommand command) {
        if (invoiceRepository.existsByInvoiceNumber(command.invoiceNumber())) {
            throw new InvoiceAlreadyExistsException("Invoice with this number already exists");
        }

        Client invoiceClient = clientRepository.findById(UUID.fromString(command.clientId()))
                .orElseThrow(() -> new InvoiceNotFoundException("Client not found"));

        // Validate line items
        if (command.lineItems() == null || command.lineItems().isEmpty()) {
            throw new IllegalArgumentException("Invoice must have at least one line item");
        }

        validateLineItems(command.lineItems());
        validateInvoiceTotals(command);

        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                command.invoiceNumber(),
                invoiceClient.getId(),
                command.clientName(),
                command.clientEmail(),
                command.issueDate(),
                command.dueDate(),
                command.subtotal(),
                command.taxPercent(),
                command.taxAmount(),
                command.totalAmount(),
                command.currency(),
                command.status(),
                null,
                command.notes(),
                Instant.now(),
                Instant.now()
        );

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Create line items
        List<InvoiceLineItem> lineItems = command.lineItems().stream()
                .map(item -> new InvoiceLineItem(
                        UUID.randomUUID(),
                        savedInvoice.getId(),
                        item.description(),
                        item.quantity(),
                        item.unitPrice(),
                        item.amount(),
                        Instant.now(),
                        Instant.now()
                ))
                .collect(Collectors.toList());

        lineItemRepository.saveAll(lineItems);

        Client savedClient = clientRepository.findById(savedInvoice.getClientId())
                .orElse(null);
        Map<UUID, Client> clientMap = new HashMap<>();
        if (savedClient != null) {
            clientMap.put(savedInvoice.getClientId(), savedClient);
        }

        return toResponseWithLineItems(savedInvoice, clientMap);
    }

    @Transactional
    public InvoiceResponse markPaid(UUID id, MarkInvoicePaidCommand command) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));

        // Validate requested status
        if (command.status() != InvoiceStatus.PAID) {
            throw new InvalidInvoiceStatusTransitionException(
                    "Only PAID status is allowed for reconciliation operation");
        }

        // Validate status transition rules
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new InvalidInvoiceStatusTransitionException(
                    "Cannot mark a VOID invoice as PAID");
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            // Idempotent: allow repeated reconciliation without error
            // paidAt may be updated to current timestamp
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        invoice.preUpdate();

        Invoice updatedInvoice = invoiceRepository.save(invoice);

        Client invoiceClient = clientRepository.findById(updatedInvoice.getClientId())
                .orElse(null);
        Map<UUID, Client> clientMap = new HashMap<>();
        if (invoiceClient != null) {
            clientMap.put(updatedInvoice.getClientId(), invoiceClient);
        }

        return toResponseWithLineItems(updatedInvoice, clientMap);
    }

    private InvoiceResponse toResponseWithLineItems(Invoice invoice, Map<UUID, Client> clientMap) {
        List<InvoiceLineItem> lineItems = lineItemRepository.findByInvoiceId(invoice.getId());

        return new InvoiceResponse(
                invoice.getId().toString(),
                invoice.getInvoiceNumber(),
                invoice.getClientId().toString(),
                invoice.getClientName(),
                invoice.getClientEmail(),
                invoice.getIssueDate().toString(),
                invoice.getDueDate().toString(),
                lineItems.stream()
                        .map(this::toLineItemResponse)
                        .collect(Collectors.toList()),
                invoice.getSubtotal(),
                invoice.getTaxPercent(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getCurrency().name(),
                invoice.getStatus().name(),
                invoice.getPaidAt() != null ? invoice.getPaidAt().toString() : null,
                invoice.getNotes(),
                invoice.getUpdatedAt().toString()
        );
    }

    private InvoiceLineItemResponse toLineItemResponse(InvoiceLineItem item) {
        return new InvoiceLineItemResponse(
                item.getId().toString(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }

    private void validateLineItems(List<CreateInvoiceCommand.CreateInvoiceLineItemCommand> lineItems) {
        for (CreateInvoiceCommand.CreateInvoiceLineItemCommand item : lineItems) {
            // Validate quantity > 0
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Line item quantity must be positive");
            }

            // Validate monetary values >= 0
            if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Line item unit price must be positive or zero");
            }

            if (item.amount() == null || item.amount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Line item amount must be positive or zero");
            }

            // Validate: amount = quantity × unitPrice (with 2 decimal rounding)
            BigDecimal calculatedAmount = new BigDecimal(item.quantity())
                    .multiply(item.unitPrice())
                    .setScale(2, RoundingMode.HALF_UP);

            if (item.amount().compareTo(calculatedAmount) != 0) {
                throw new IllegalArgumentException(
                        String.format("Line item amount %s does not match quantity %d × unit price %s = %s",
                                item.amount(), item.quantity(), item.unitPrice(), calculatedAmount));
            }
        }
    }

    private void validateInvoiceTotals(CreateInvoiceCommand command) {
        // Calculate subtotal from line items
        BigDecimal calculatedSubtotal = command.lineItems().stream()
                .map(CreateInvoiceCommand.CreateInvoiceLineItemCommand::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (command.subtotal().compareTo(calculatedSubtotal) != 0) {
            throw new IllegalArgumentException(
                    String.format("Invoice subtotal %s does not match sum of line item amounts %s",
                            command.subtotal(), calculatedSubtotal));
        }

        // Calculate tax amount
        BigDecimal calculatedTaxAmount = command.subtotal()
                .multiply(command.taxPercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        if (command.taxAmount().compareTo(calculatedTaxAmount) != 0) {
            throw new IllegalArgumentException(
                    String.format("Invoice tax amount %s does not match subtotal %s × tax percent %s%% = %s",
                            command.taxAmount(), command.subtotal(), command.taxPercent(), calculatedTaxAmount));
        }

        // Calculate total amount
        BigDecimal calculatedTotal = command.subtotal()
                .add(command.taxAmount())
                .setScale(2, RoundingMode.HALF_UP);

        if (command.totalAmount().compareTo(calculatedTotal) != 0) {
            throw new IllegalArgumentException(
                    String.format("Invoice total amount %s does not match subtotal %s + tax amount %s = %s",
                            command.totalAmount(), command.subtotal(), command.taxAmount(), calculatedTotal));
        }
    }
}
