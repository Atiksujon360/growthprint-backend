package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoicePaymentReport;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import com.growthprint.backend.invoice.entity.PaymentMethod;
import com.growthprint.backend.invoice.entity.PaymentReportStatus;
import com.growthprint.backend.invoice.exception.InvoiceNotFoundException;
import com.growthprint.backend.invoice.repository.InvoiceLineItemRepository;
import com.growthprint.backend.invoice.repository.InvoicePaymentReportRepository;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.CLIENT_ID;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.client;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.standardInvoice;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.standardItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoicePdfServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineItemRepository lineItemRepository;
    @Mock private InvoicePaymentReportRepository paymentReportRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private AuthService authService;
    @Mock private InvoicePdfRenderer renderer;

    private InvoicePdfService service;

    @BeforeEach
    void setUp() {
        service = new InvoicePdfService(invoiceRepository, lineItemRepository, paymentReportRepository,
                clientRepository, authService, renderer);
    }

    @Test
    void adminPdfIsRenderedFromPersistedDataWithOnlyVerifiedPaymentsCountedAsPaid() {
        UUID id = UUID.randomUUID();
        Invoice invoice = standardInvoice(id, InvoiceStatus.ISSUED);
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoice));
        when(lineItemRepository.findByInvoiceId(id)).thenReturn(standardItems(id));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(paymentReportRepository.findByInvoiceIdOrderByCreatedAtDesc(id)).thenReturn(List.of(
                report(id, "300.00", PaymentReportStatus.VERIFIED),
                report(id, "200.00", PaymentReportStatus.VERIFIED),
                report(id, "999.00", PaymentReportStatus.PENDING),
                report(id, "111.00", PaymentReportStatus.REJECTED)));
        when(renderer.render(any())).thenReturn(new byte[]{1, 2, 3});

        InvoicePdf pdf = service.renderForAdmin(id);

        ArgumentCaptor<InvoicePdfData> data = ArgumentCaptor.forClass(InvoicePdfData.class);
        verify(renderer).render(data.capture());
        assertThat(data.getValue().invoice()).isSameAs(invoice);
        assertThat(data.getValue().lineItems()).hasSize(2);
        assertThat(data.getValue().client().getLegalName()).isEqualTo("Northwind Trading Ltd");
        assertThat(data.getValue().verifiedPayments()).isEqualByComparingTo("500.00");
        assertThat(data.getValue().balanceDue()).isEqualByComparingTo("710.00");
        assertThat(pdf.filename()).isEqualTo("GrowthPrint-Invoice-INV-2026-0042.pdf");
        assertThat(pdf.content()).containsExactly(1, 2, 3);
    }

    @Test
    void adminPdfForUnknownInvoiceIsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renderForAdmin(id)).isInstanceOf(InvoiceNotFoundException.class);
        verify(renderer, never()).render(any());
    }

    @Test
    void clientCanDownloadOwnInvoice() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(authService.getUserById(userId)).thenReturn(clientUser(userId, CLIENT_ID));
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(standardInvoice(id, InvoiceStatus.ISSUED)));
        when(lineItemRepository.findByInvoiceId(id)).thenReturn(standardItems(id));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(paymentReportRepository.findByInvoiceIdOrderByCreatedAtDesc(id)).thenReturn(List.of());
        when(renderer.render(any())).thenReturn(new byte[]{9});

        InvoicePdf pdf = service.renderForClient(id, userId.toString());

        assertThat(pdf.content()).containsExactly(9);
    }

    @Test
    void anotherClientsInvoiceIsReportedAsNotFound() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(authService.getUserById(userId)).thenReturn(clientUser(userId, UUID.randomUUID()));
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(standardInvoice(id, InvoiceStatus.ISSUED)));

        assertThatThrownBy(() -> service.renderForClient(id, userId.toString()))
                .isInstanceOf(InvoiceNotFoundException.class);
        verify(renderer, never()).render(any());
    }

    @Test
    void userWithoutClientIsRejected() {
        UUID userId = UUID.randomUUID();
        when(authService.getUserById(userId)).thenReturn(clientUser(userId, null));

        assertThatThrownBy(() -> service.renderForClient(UUID.randomUUID(), userId.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filenameIsSanitised() {
        assertThat(InvoicePdfService.filenameFor("INV-2026-0042")).isEqualTo("GrowthPrint-Invoice-INV-2026-0042.pdf");
        assertThat(InvoicePdfService.filenameFor("INV/2026 \"7\"")).isEqualTo("GrowthPrint-Invoice-INV-2026--7-.pdf");
    }

    private static InvoicePaymentReport report(UUID invoiceId, String amount, PaymentReportStatus status) {
        InvoicePaymentReport report = new InvoicePaymentReport(UUID.randomUUID(), invoiceId, CLIENT_ID,
                UUID.randomUUID(), new BigDecimal(amount), InvoiceCurrency.EUR, PaymentMethod.BANK_TRANSFER, null,
                LocalDate.of(2026, 11, 1), null, Instant.now());
        if (status != PaymentReportStatus.PENDING) {
            report.review(status, UUID.randomUUID(), "reviewed", Instant.now());
        }
        return report;
    }

    private static User clientUser(UUID userId, UUID clientId) {
        return new User(userId, "client@example.test", "client@example.test", "hash", UserRole.CLIENT,
                AccountStatus.ACTIVE, clientId, Instant.now(), Instant.now());
    }
}
