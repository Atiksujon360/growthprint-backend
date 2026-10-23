package com.growthprint.backend.invoice.controller;

import com.growthprint.backend.invoice.dto.CreateInvoiceCommand;
import com.growthprint.backend.invoice.dto.InvoiceResponse;
import com.growthprint.backend.invoice.dto.MarkInvoicePaidCommand;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import com.growthprint.backend.invoice.exception.InvoiceNotFoundException;
import com.growthprint.backend.invoice.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    @Mock
    private InvoiceService invoiceService;

    @Test
    void listReturnsAllInvoices() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        InvoiceResponse response1 = new InvoiceResponse(
                id1.toString(), "INV-2026-0041", "client-1", "Client 1", "billing@client1.com",
                "2026-01-15", "2026-01-30", List.of(),
                new BigDecimal("1000.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("1000.00"), "USD", "ISSUED", null, "Test notes", "2026-01-15T00:00:00Z"
        );

        InvoiceResponse response2 = new InvoiceResponse(
                id2.toString(), "INV-2026-0042", "client-2", "Client 2", "billing@client2.com",
                "2026-01-16", "2026-01-31", List.of(),
                new BigDecimal("2000.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("2000.00"), "USD", "PAID", "2026-01-20T00:00:00Z", "Paid", "2026-01-16T00:00:00Z"
        );

        when(invoiceService.findAll()).thenReturn(List.of(response1, response2));

        InvoiceController controller = new InvoiceController(invoiceService);
        List<InvoiceResponse> result = controller.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).invoiceNumber()).isEqualTo("INV-2026-0041");
        assertThat(result.get(0).status()).isEqualTo("ISSUED");
        assertThat(result.get(1).invoiceNumber()).isEqualTo("INV-2026-0042");
        assertThat(result.get(1).status()).isEqualTo("PAID");
    }

    @Test
    void getByIdReturnsInvoice() {
        UUID id = UUID.randomUUID();

        InvoiceResponse response = new InvoiceResponse(
                id.toString(), "INV-2026-0041", "client-1", "Client 1", "billing@client1.com",
                "2026-01-15", "2026-01-30", List.of(),
                new BigDecimal("1000.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("1000.00"), "USD", "ISSUED", null, "Test notes", "2026-01-15T00:00:00Z"
        );

        when(invoiceService.findById(id)).thenReturn(response);

        InvoiceController controller = new InvoiceController(invoiceService);
        InvoiceResponse result = controller.getById(id);

        assertThat(result.id()).isEqualTo(id.toString());
        assertThat(result.invoiceNumber()).isEqualTo("INV-2026-0041");
        assertThat(result.clientName()).isEqualTo("Client 1");
        assertThat(result.status()).isEqualTo("ISSUED");
        assertThat(result.totalAmount()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void getByIdReturns404ForUnknownId() {
        UUID id = UUID.randomUUID();

        when(invoiceService.findById(id))
                .thenThrow(new InvoiceNotFoundException("Invoice not found"));

        InvoiceController controller = new InvoiceController(invoiceService);

        assertThatThrownBy(() -> controller.getById(id))
                .isInstanceOf(InvoiceNotFoundException.class)
                .hasMessageContaining("Invoice not found");
    }

    @Test
    void createReturnsInvoice() {
        UUID id = UUID.randomUUID();
        CreateInvoiceCommand.CreateInvoiceLineItemCommand lineItem =
                new CreateInvoiceCommand.CreateInvoiceLineItemCommand(
                        "Test Service", 1, new BigDecimal("1000.00"), new BigDecimal("1000.00")
                );

        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "INV-2026-0043", "client-1", "Client 1", "billing@client1.com",
                LocalDate.now(), LocalDate.now().plusDays(15),
                new BigDecimal("1000.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("1000.00"), InvoiceCurrency.USD, InvoiceStatus.ISSUED, "Test notes",
                List.of(lineItem)
        );

        InvoiceResponse response = new InvoiceResponse(
                id.toString(), "INV-2026-0043", "client-1", "Client 1", "billing@client1.com",
                LocalDate.now().toString(), LocalDate.now().plusDays(15).toString(), List.of(),
                new BigDecimal("1000.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("1000.00"), "USD", "ISSUED", null, "Test notes", "2026-01-15T00:00:00Z"
        );

        when(invoiceService.create(command)).thenReturn(response);

        InvoiceController controller = new InvoiceController(invoiceService);
        InvoiceResponse result = controller.create(command);

        assertThat(result.invoiceNumber()).isEqualTo("INV-2026-0043");
        assertThat(result.status()).isEqualTo("ISSUED");
    }

    @Test
    void markPaidReturnsInvoice() {
        UUID id = UUID.randomUUID();
        MarkInvoicePaidCommand command = new MarkInvoicePaidCommand(InvoiceStatus.PAID);

        InvoiceResponse response = new InvoiceResponse(
                id.toString(), "INV-2026-0041", "client-1", "Client 1", "billing@client1.com",
                "2026-01-15", "2026-01-30", List.of(),
                new BigDecimal("1000.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("1000.00"), "USD", "PAID", "2026-01-20T00:00:00Z", "Paid", "2026-01-20T00:00:00Z"
        );

        when(invoiceService.markPaid(id, command)).thenReturn(response);

        InvoiceController controller = new InvoiceController(invoiceService);
        InvoiceResponse result = controller.markPaid(id, command);

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.paidAt()).isNotNull();
    }
}
