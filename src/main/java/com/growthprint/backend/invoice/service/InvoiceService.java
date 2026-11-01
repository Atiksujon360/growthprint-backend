package com.growthprint.backend.invoice.service;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.exception.ClientNotFoundException;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.invoice.dto.CreateInvoiceCommand;
import com.growthprint.backend.invoice.dto.InvoiceLineItemResponse;
import com.growthprint.backend.invoice.dto.InvoiceResponse;
import com.growthprint.backend.invoice.dto.MarkInvoicePaidCommand;
import com.growthprint.backend.invoice.entity.Invoice;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    /**
     * Statuses an invoice may be created in. PAID needs a paid_at timestamp and an authorised
     * reconciliation, VOID/OVERDUE are lifecycle outcomes; none of them may be set at creation time.
     */
    private static final Set<InvoiceStatus> CREATABLE_STATUSES = EnumSet.of(InvoiceStatus.DRAFT, InvoiceStatus.ISSUED);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final ClientRepository clientRepository;
    private final AuthService authService;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceLineItemRepository lineItemRepository,
                          ClientRepository clientRepository, AuthService authService) {
        this.invoiceRepository = invoiceRepository;
        this.lineItemRepository = lineItemRepository;
        this.clientRepository = clientRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findAll() {
        return toResponses(invoiceRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findByClientId(UUID clientId) {
        return toResponses(invoiceRepository.findByClientId(clientId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findMyInvoices(String userId) {
        User user = authService.getUserById(UUID.fromString(userId));
        if (user.getClientId() == null) {
            throw new IllegalArgumentException("User is not associated with a client");
        }
        return toResponses(invoiceRepository.findByClientId(user.getClientId()));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));
        return toResponse(invoice, lineItemRepository.findByInvoiceId(invoice.getId()));
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceCommand command) {
        if (!CREATABLE_STATUSES.contains(command.status())) {
            throw new IllegalArgumentException("Invoices can only be created with status DRAFT or ISSUED");
        }

        if (invoiceRepository.existsByInvoiceNumber(command.invoiceNumber())) {
            throw new InvoiceAlreadyExistsException("Invoice with this number already exists");
        }

        UUID clientId = parseUuid(command.clientId());
        Client invoiceClient = clientRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));

        // Validate line items
        if (command.lineItems() == null || command.lineItems().isEmpty()) {
            throw new IllegalArgumentException("Invoice must have at least one line item");
        }

        validateLineItems(command.lineItems());
        validateInvoiceTotals(command);

        if (command.dueDate().isBefore(command.issueDate())) {
            throw new IllegalArgumentException("Due date must not be before the issue date");
        }

        Instant now = Instant.now();
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
                now,
                now
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
                        now,
                        now
                ))
                .collect(Collectors.toList());

        lineItemRepository.saveAll(lineItems);

        return toResponse(savedInvoice, lineItems);
    }

    /**
     * Admin reconciliation: the only path (directly, or via a verified client payment report) that marks an
     * invoice PAID. Idempotent for already-paid invoices.
     */
    @Transactional
    public InvoiceResponse markPaid(UUID id, MarkInvoicePaidCommand command) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));

        // Validate requested status
        if (command.status() != InvoiceStatus.PAID) {
            throw new InvalidInvoiceStatusTransitionException(
                    "Only PAID status is allowed for reconciliation operation");
        }

        return toResponse(applyPaid(invoice), lineItemRepository.findByInvoiceId(invoice.getId()));
    }

    /**
     * Shared PAID transition used by {@link #markPaid} and by payment-report verification.
     * Must be called inside a transaction.
     */
    public Invoice applyPaid(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new InvalidInvoiceStatusTransitionException("Cannot mark a VOID invoice as PAID");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return invoice; // idempotent
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        invoice.preUpdate();
        return invoiceRepository.save(invoice);
    }

    /** Line items are batch-loaded: one query for the whole list instead of one per invoice. */
    private List<InvoiceResponse> toResponses(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<InvoiceLineItem>> itemsByInvoice = lineItemRepository
                .findByInvoiceIdIn(invoices.stream().map(Invoice::getId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(InvoiceLineItem::getInvoiceId));
        return invoices.stream()
                .map(invoice -> toResponse(invoice, itemsByInvoice.getOrDefault(invoice.getId(), List.of())))
                .collect(Collectors.toList());
    }

    InvoiceResponse toResponse(Invoice invoice, List<InvoiceLineItem> lineItems) {
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
                invoice.getUpdatedAt() != null ? invoice.getUpdatedAt().toString() : null
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

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("clientId must be a valid UUID");
        }
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

            // Validate: amount = quantity × unitPrice (with 2 decimal rounding); mirrors the V14 trigger
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
