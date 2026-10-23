package com.growthprint.backend.invoice.service;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.invoice.dto.CreateInvoiceCommand;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceLineItemRepository lineItemRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    private Client testClient;
    private Invoice testInvoice;
    private InvoiceLineItem testLineItem;

    @BeforeEach
    void setUp() {
        testClient = new Client(
                UUID.randomUUID(),
                "Test Client",
                "Test Client Legal",
                "test@example.com",
                null,
                "https://example.com",
                ClientEnums.ClientStatus.ACTIVE,
                null,
                null,
                null,
                LocalDate.now(),
                "Test Manager",
                ClientEnums.BillingCurrency.USD,
                null
        );

        testInvoice = new Invoice(
                UUID.randomUUID(),
                "INV-2026-0041",
                testClient.getId(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1000.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                null,
                "Test notes",
                Instant.now(),
                Instant.now()
        );

        testLineItem = new InvoiceLineItem(
                UUID.randomUUID(),
                testInvoice.getId(),
                "Test Description",
                1,
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void findAll_ReturnsAllInvoices() {
        when(invoiceRepository.findAll()).thenReturn(List.of(testInvoice));
        when(lineItemRepository.findByInvoiceId(testInvoice.getId())).thenReturn(List.of(testLineItem));
        when(clientRepository.findAllById(List.of(testClient.getId()))).thenReturn(List.of(testClient));

        var result = invoiceService.findAll();

        assertEquals(1, result.size());
        assertEquals("INV-2026-0041", result.get(0).invoiceNumber());
    }

    @Test
    void findById_ReturnsInvoice() {
        when(invoiceRepository.findById(testInvoice.getId())).thenReturn(Optional.of(testInvoice));
        when(lineItemRepository.findByInvoiceId(testInvoice.getId())).thenReturn(List.of(testLineItem));
        when(clientRepository.findById(testInvoice.getClientId())).thenReturn(Optional.of(testClient));

        var result = invoiceService.findById(testInvoice.getId());

        assertEquals("INV-2026-0041", result.invoiceNumber());
        assertEquals("Test Client", result.clientName());
    }

    @Test
    void findById_ThrowsWhenNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(InvoiceNotFoundException.class, () -> invoiceService.findById(UUID.randomUUID()));
    }

    @Test
    void create_CreatesInvoice() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        1,
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1000.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber("INV-2026-0042")).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(invoiceRepository.save(any())).thenReturn(testInvoice);
        when(lineItemRepository.saveAll(any())).thenReturn(List.of(testLineItem));
        when(clientRepository.findById(testInvoice.getClientId())).thenReturn(Optional.of(testClient));

        var result = invoiceService.create(command);

        assertNotNull(result);
        verify(invoiceRepository).save(any(Invoice.class));
        verify(lineItemRepository).saveAll(any());
    }

    @Test
    void create_ThrowsWhenInvoiceNumberExists() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        1,
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0041",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1000.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber("INV-2026-0041")).thenReturn(true);

        assertThrows(InvoiceAlreadyExistsException.class, () -> invoiceService.create(command));
    }

    @Test
    void create_ThrowsWhenClientNotFound() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        1,
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                UUID.randomUUID().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1000.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(InvoiceNotFoundException.class, () -> invoiceService.create(command));
    }

    @Test
    void markPaid_MarksInvoiceAsPaid() {
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(InvoiceStatus.PAID);

        when(invoiceRepository.findById(testInvoice.getId())).thenReturn(Optional.of(testInvoice));
        when(invoiceRepository.save(any())).thenReturn(testInvoice);
        when(lineItemRepository.findByInvoiceId(testInvoice.getId())).thenReturn(List.of(testLineItem));
        when(clientRepository.findById(testInvoice.getClientId())).thenReturn(Optional.of(testClient));

        var result = invoiceService.markPaid(testInvoice.getId(), command);

        assertEquals(InvoiceStatus.PAID, testInvoice.getStatus());
        assertNotNull(testInvoice.getPaidAt());
        verify(invoiceRepository).save(testInvoice);
    }

    @Test
    void markPaid_ThrowsWhenNotFound() {
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(InvoiceStatus.PAID);

        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(InvoiceNotFoundException.class, () -> invoiceService.markPaid(UUID.randomUUID(), command));
    }

    @Test
    void markPaid_ThrowsWhenInvalidStatus() {
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(InvoiceStatus.ISSUED);

        when(invoiceRepository.findById(testInvoice.getId())).thenReturn(Optional.of(testInvoice));

        assertThrows(InvalidInvoiceStatusTransitionException.class, () -> invoiceService.markPaid(testInvoice.getId(), command));
    }

    @Test
    void markPaid_ThrowsWhenInvoiceIsVoid() {
        testInvoice.setStatus(InvoiceStatus.VOID);
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(InvoiceStatus.PAID);

        when(invoiceRepository.findById(testInvoice.getId())).thenReturn(Optional.of(testInvoice));

        assertThrows(InvalidInvoiceStatusTransitionException.class, () -> invoiceService.markPaid(testInvoice.getId(), command));
    }

    @Test
    void markPaid_AllowsReconciliationOfAlreadyPaidInvoice() {
        testInvoice.setStatus(InvoiceStatus.PAID);
        testInvoice.setPaidAt(Instant.now().minusSeconds(3600));
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(InvoiceStatus.PAID);

        when(invoiceRepository.findById(testInvoice.getId())).thenReturn(Optional.of(testInvoice));
        when(invoiceRepository.save(any())).thenReturn(testInvoice);
        when(lineItemRepository.findByInvoiceId(testInvoice.getId())).thenReturn(List.of(testLineItem));
        when(clientRepository.findById(testInvoice.getClientId())).thenReturn(Optional.of(testClient));

        var result = invoiceService.markPaid(testInvoice.getId(), command);

        assertNotNull(result);
        verify(invoiceRepository).save(testInvoice);
    }

    @Test
    void create_ThrowsWhenLineItemsEmpty() {
        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1000.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of()
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(command));
    }

    @Test
    void create_ThrowsWhenLineItemAmountMismatch() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        2,
                        new BigDecimal("100.00"),
                        new BigDecimal("300.00") // Wrong: should be 200.00
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("300.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("300.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(command));
    }

    @Test
    void create_ThrowsWhenSubtotalMismatch() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        1,
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("2000.00"), // Wrong: should be 1000.00
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("2000.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(command));
    }

    @Test
    void create_ThrowsWhenTaxAmountMismatch() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        1,
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"),
                new BigDecimal("10.00"),
                new BigDecimal("200.00"), // Wrong: should be 100.00
                new BigDecimal("1100.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(command));
    }

    @Test
    void create_ThrowsWhenTotalAmountMismatch() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        1,
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"),
                new BigDecimal("10.00"),
                new BigDecimal("100.00"),
                new BigDecimal("1500.00"), // Wrong: should be 1100.00
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(command));
    }

    @Test
    void create_ThrowsWhenLineItemQuantityZero() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        0, // Invalid: must be positive
                        new BigDecimal("1000.00"),
                        new BigDecimal("0.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(command));
    }

    @Test
    void create_ThrowsWhenLineItemUnitPriceNegative() {
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItemCmd =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Description",
                        1,
                        new BigDecimal("-100.00"), // Invalid: must be >= 0
                        new BigDecimal("-100.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0042",
                testClient.getId().toString(),
                "Test Client",
                "billing@example.com",
                LocalDate.now(),
                LocalDate.now().plusDays(15),
                new BigDecimal("-100.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("-100.00"),
                InvoiceCurrency.USD,
                InvoiceStatus.ISSUED,
                "Test notes",
                List.of(lineItemCmd)
        );

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(command));
    }
}
