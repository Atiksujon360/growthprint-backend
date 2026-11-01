package com.growthprint.backend.invoice.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.invoice.dto.PaymentReportResponse;
import com.growthprint.backend.invoice.dto.ReportPaymentCommand;
import com.growthprint.backend.invoice.dto.ReviewPaymentReportCommand;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoicePaymentReport;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import com.growthprint.backend.invoice.entity.PaymentMethod;
import com.growthprint.backend.invoice.entity.PaymentReportStatus;
import com.growthprint.backend.invoice.exception.InvalidInvoiceStatusTransitionException;
import com.growthprint.backend.invoice.exception.InvoiceNotFoundException;
import com.growthprint.backend.invoice.repository.InvoiceLineItemRepository;
import com.growthprint.backend.invoice.repository.InvoicePaymentReportRepository;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Client payment reporting: a CLIENT may report a payment but can never mark an invoice PAID;
 * only an ADMIN verification does that.
 */
class InvoicePaymentReportServiceTest {

    private final InvoicePaymentReportRepository reportRepository = mock(InvoicePaymentReportRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final InvoiceLineItemRepository lineItemRepository = mock(InvoiceLineItemRepository.class);
    private final AuthService authService = mock(AuthService.class);

    private InvoicePaymentReportService service;

    private final UUID clientId = UUID.randomUUID();
    private final UUID clientUserId = UUID.randomUUID();
    private final UUID adminUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        InvoiceService invoiceService = new InvoiceService(invoiceRepository, lineItemRepository,
                mock(ClientRepository.class), authService);
        service = new InvoicePaymentReportService(reportRepository, invoiceRepository, invoiceService, authService);

        when(authService.getUserById(clientUserId)).thenReturn(user(clientUserId, UserRole.CLIENT, clientId));
        when(authService.getUserById(adminUserId)).thenReturn(user(adminUserId, UserRole.ADMIN, null));
        when(reportRepository.save(any(InvoicePaymentReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---- client reports ------------------------------------------------------------------------

    @Test
    void clientCanReportAPaymentButTheInvoiceStaysUnpaid() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);

        PaymentReportResponse response = service.reportPayment(invoice.getId(), command(), clientUserId.toString());

        ArgumentCaptor<InvoicePaymentReport> saved = ArgumentCaptor.forClass(InvoicePaymentReport.class);
        verify(reportRepository).save(saved.capture());
        InvoicePaymentReport report = saved.getValue();
        assertThat(report.getStatus()).isEqualTo(PaymentReportStatus.PENDING);
        assertThat(report.getClientId()).isEqualTo(clientId);
        assertThat(report.getReportedByUserId()).isEqualTo(clientUserId);
        assertThat(report.getCurrency()).isEqualTo(InvoiceCurrency.EUR); // from the invoice, not client input

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getPaidAt()).isNull();
        verify(invoiceRepository, never()).save(any());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.invoiceStatus()).isEqualTo("ISSUED");
    }

    @Test
    void overdueInvoicesCanAlsoBeReported() {
        Invoice invoice = invoice(clientId, InvoiceStatus.OVERDUE);

        assertThat(service.reportPayment(invoice.getId(), command(), clientUserId.toString()).status())
                .isEqualTo("PENDING");
    }

    @Test
    void anotherClientsInvoiceIsReportedAsNotFound() {
        Invoice foreign = invoice(UUID.randomUUID(), InvoiceStatus.ISSUED);

        assertThatThrownBy(() -> service.reportPayment(foreign.getId(), command(), clientUserId.toString()))
                .isInstanceOf(InvoiceNotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceStatus.class, names = {"DRAFT", "PAID", "VOID"})
    void onlyIssuedOrOverdueInvoicesAcceptReports(InvoiceStatus status) {
        Invoice invoice = invoice(clientId, status);

        assertThatThrownBy(() -> service.reportPayment(invoice.getId(), command(), clientUserId.toString()))
                .isInstanceOf(IllegalStateException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void aSecondReportWhileOneIsPendingIsAConflict() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);
        when(reportRepository.existsByInvoiceIdAndStatus(invoice.getId(), PaymentReportStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reportPayment(invoice.getId(), command(), clientUserId.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void clientCannotListReportsOfAnotherClientsInvoice() {
        Invoice foreign = invoice(UUID.randomUUID(), InvoiceStatus.ISSUED);

        assertThatThrownBy(() -> service.findMyReports(foreign.getId(), clientUserId.toString()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void clientSeesReportsOfOwnInvoice() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);
        InvoicePaymentReport report = pendingReport(invoice);
        when(reportRepository.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId())).thenReturn(List.of(report));

        assertThat(service.findMyReports(invoice.getId(), clientUserId.toString()))
                .extracting(PaymentReportResponse::id).containsExactly(report.getId().toString());
    }

    // ---- admin review --------------------------------------------------------------------------

    @Test
    void adminVerificationMarksTheInvoicePaid() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);
        InvoicePaymentReport report = pendingReport(invoice);

        PaymentReportResponse response = service.verify(report.getId(),
                new ReviewPaymentReportCommand("Matched bank statement"), adminUserId.toString());

        assertThat(report.getStatus()).isEqualTo(PaymentReportStatus.VERIFIED);
        assertThat(report.getReviewedByUserId()).isEqualTo(adminUserId);
        assertThat(report.getReviewedAt()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull();
        assertThat(response.invoiceStatus()).isEqualTo("PAID");
    }

    @Test
    void verifyingWorksWithoutABody() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);
        InvoicePaymentReport report = pendingReport(invoice);

        assertThat(service.verify(report.getId(), null, adminUserId.toString()).status()).isEqualTo("VERIFIED");
    }

    @Test
    void reviewedReportsCannotBeReviewedAgain() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);
        InvoicePaymentReport report = pendingReport(invoice);
        report.review(PaymentReportStatus.REJECTED, adminUserId, "No payment found", Instant.now());

        assertThatThrownBy(() -> service.verify(report.getId(), null, adminUserId.toString()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void verifyingAReportForAVoidInvoiceIsRejected() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);
        InvoicePaymentReport report = pendingReport(invoice);
        invoice.setStatus(InvoiceStatus.VOID);

        assertThatThrownBy(() -> service.verify(report.getId(), null, adminUserId.toString()))
                .isInstanceOf(InvalidInvoiceStatusTransitionException.class);
        assertThat(report.getStatus()).isEqualTo(PaymentReportStatus.PENDING);
    }

    @Test
    void rejectingRequiresAReasonAndLeavesTheInvoiceUnchanged() {
        Invoice invoice = invoice(clientId, InvoiceStatus.ISSUED);
        InvoicePaymentReport report = pendingReport(invoice);

        assertThatThrownBy(() -> service.reject(report.getId(), new ReviewPaymentReportCommand("  "),
                adminUserId.toString())).isInstanceOf(IllegalArgumentException.class);

        PaymentReportResponse response = service.reject(report.getId(),
                new ReviewPaymentReportCommand("No matching payment received"), adminUserId.toString());

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reviewNote()).isEqualTo("No matching payment received");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void adminListForUnknownInvoiceIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(invoiceRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findReports(unknown)).isInstanceOf(InvoiceNotFoundException.class);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private ReportPaymentCommand command() {
        return new ReportPaymentCommand(new BigDecimal("1210.00"), PaymentMethod.BANK_TRANSFER, "ACH-123",
                LocalDate.now().minusDays(1), "Paid in full");
    }

    private Invoice invoice(UUID owner, InvoiceStatus status) {
        Invoice invoice = new Invoice(UUID.randomUUID(), "INV-2026-0001", owner, "Acme", "ap@acme.test",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(20), new BigDecimal("1000.00"),
                new BigDecimal("21.00"), new BigDecimal("210.00"), new BigDecimal("1210.00"), InvoiceCurrency.EUR,
                status, null, null, Instant.now(), Instant.now());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        return invoice;
    }

    private InvoicePaymentReport pendingReport(Invoice invoice) {
        InvoicePaymentReport report = new InvoicePaymentReport(UUID.randomUUID(), invoice.getId(),
                invoice.getClientId(), clientUserId, new BigDecimal("1210.00"), invoice.getCurrency(),
                PaymentMethod.BANK_TRANSFER, "ACH-123", LocalDate.now().minusDays(1), null, Instant.now());
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        return report;
    }

    private static User user(UUID id, UserRole role, UUID clientId) {
        return new User(id, id + "@example.test", id + "@example.test", "hash", role, AccountStatus.ACTIVE,
                clientId, Instant.now(), Instant.now());
    }
}
