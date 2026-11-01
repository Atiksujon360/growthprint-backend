package com.growthprint.backend.invoice.service;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.invoice.dto.PaymentReportResponse;
import com.growthprint.backend.invoice.dto.ReportPaymentCommand;
import com.growthprint.backend.invoice.dto.ReviewPaymentReportCommand;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoicePaymentReport;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import com.growthprint.backend.invoice.entity.PaymentReportStatus;
import com.growthprint.backend.invoice.exception.InvoiceNotFoundException;
import com.growthprint.backend.invoice.repository.InvoicePaymentReportRepository;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Client-reported invoice payments.
 * <ul>
 *     <li>A CLIENT reports a payment for one of its own ISSUED/OVERDUE invoices. This creates a PENDING report
 *     and <b>never</b> changes the invoice.</li>
 *     <li>An ADMIN verifies the report (invoice becomes PAID through {@link InvoiceService#applyPaid}, the same
 *     rule as the admin reconcile endpoint) or rejects it with a reason (invoice unchanged).</li>
 * </ul>
 * Error mapping: other clients' invoices are reported as not found (404, ids cannot be probed); business-state
 * conflicts are {@link IllegalStateException} (409); invalid input is {@link IllegalArgumentException} (400).
 */
@Service
public class InvoicePaymentReportService {

    private static final Set<InvoiceStatus> REPORTABLE_STATUSES = EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE);

    private final InvoicePaymentReportRepository reportRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final AuthService authService;

    public InvoicePaymentReportService(InvoicePaymentReportRepository reportRepository,
                                       InvoiceRepository invoiceRepository, InvoiceService invoiceService,
                                       AuthService authService) {
        this.reportRepository = reportRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.authService = authService;
    }

    @Transactional
    public PaymentReportResponse reportPayment(UUID invoiceId, ReportPaymentCommand command, String userId) {
        User user = authService.getUserById(UUID.fromString(userId));
        Invoice invoice = loadOwnedInvoice(invoiceId, user);

        if (!REPORTABLE_STATUSES.contains(invoice.getStatus())) {
            throw new IllegalStateException("Payments can only be reported for ISSUED or OVERDUE invoices (current: "
                    + invoice.getStatus() + ")");
        }
        // Friendly check; the partial unique index uq_payment_reports_one_pending_per_invoice enforces it
        // under concurrency (DataIntegrityViolation -> 409).
        if (reportRepository.existsByInvoiceIdAndStatus(invoice.getId(), PaymentReportStatus.PENDING)) {
            throw new IllegalStateException("A payment report for this invoice is already pending review");
        }

        InvoicePaymentReport report = new InvoicePaymentReport(
                UUID.randomUUID(),
                invoice.getId(),
                invoice.getClientId(),
                user.getId(),
                command.amount(),
                invoice.getCurrency(),
                command.paymentMethod(),
                blankToNull(command.reference()),
                command.paidOn(),
                blankToNull(command.note()),
                Instant.now());

        return toResponse(reportRepository.save(report), invoice);
    }

    @Transactional(readOnly = true)
    public List<PaymentReportResponse> findMyReports(UUID invoiceId, String userId) {
        User user = authService.getUserById(UUID.fromString(userId));
        Invoice invoice = loadOwnedInvoice(invoiceId, user);
        return toResponses(invoice);
    }

    @Transactional(readOnly = true)
    public List<PaymentReportResponse> findReports(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));
        return toResponses(invoice);
    }

    @Transactional
    public PaymentReportResponse verify(UUID reportId, ReviewPaymentReportCommand command, String adminUserId) {
        InvoicePaymentReport report = loadPendingReport(reportId);
        Invoice invoice = invoiceRepository.findById(report.getInvoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));

        // Marks the invoice PAID (or rejects a VOID invoice) before the report is touched.
        Invoice paid = invoiceService.applyPaid(invoice);

        report.review(PaymentReportStatus.VERIFIED, UUID.fromString(adminUserId),
                command != null ? blankToNull(command.note()) : null, Instant.now());
        return toResponse(reportRepository.save(report), paid);
    }

    @Transactional
    public PaymentReportResponse reject(UUID reportId, ReviewPaymentReportCommand command, String adminUserId) {
        String reason = command != null ? blankToNull(command.note()) : null;
        if (reason == null) {
            throw new IllegalArgumentException("A note explaining the rejection is required");
        }
        InvoicePaymentReport report = loadPendingReport(reportId);
        Invoice invoice = invoiceRepository.findById(report.getInvoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));

        report.review(PaymentReportStatus.REJECTED, UUID.fromString(adminUserId), reason, Instant.now());
        return toResponse(reportRepository.save(report), invoice);
    }

    /** Ownership check: another client's invoice is indistinguishable from a missing one. */
    private Invoice loadOwnedInvoice(UUID invoiceId, User user) {
        if (user.getClientId() == null) {
            throw new IllegalArgumentException("User is not associated with a client");
        }
        return invoiceRepository.findById(invoiceId)
                .filter(invoice -> user.getClientId().equals(invoice.getClientId()))
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));
    }

    private InvoicePaymentReport loadPendingReport(UUID reportId) {
        InvoicePaymentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new InvoiceNotFoundException("Payment report not found"));
        if (report.getStatus() != PaymentReportStatus.PENDING) {
            throw new IllegalStateException("Payment report has already been reviewed (" + report.getStatus() + ")");
        }
        return report;
    }

    private List<PaymentReportResponse> toResponses(Invoice invoice) {
        return reportRepository.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).stream()
                .map(report -> toResponse(report, invoice))
                .toList();
    }

    private static PaymentReportResponse toResponse(InvoicePaymentReport report, Invoice invoice) {
        return new PaymentReportResponse(
                report.getId().toString(),
                report.getInvoiceId().toString(),
                invoice.getInvoiceNumber(),
                invoice.getStatus().name(),
                report.getClientId().toString(),
                report.getAmount(),
                report.getCurrency().name(),
                report.getPaymentMethod().name(),
                report.getReference(),
                report.getPaidOn().toString(),
                report.getNote(),
                report.getStatus().name(),
                report.getReportedByUserId().toString(),
                report.getCreatedAt() != null ? report.getCreatedAt().toString() : null,
                report.getReviewedAt() != null ? report.getReviewedAt().toString() : null,
                report.getReviewNote());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
