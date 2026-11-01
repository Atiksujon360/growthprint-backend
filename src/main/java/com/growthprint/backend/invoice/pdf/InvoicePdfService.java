package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoicePaymentReport;
import com.growthprint.backend.invoice.entity.PaymentReportStatus;
import com.growthprint.backend.invoice.exception.InvoiceNotFoundException;
import com.growthprint.backend.invoice.repository.InvoiceLineItemRepository;
import com.growthprint.backend.invoice.repository.InvoicePaymentReportRepository;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Builds invoice PDFs on demand from the persisted invoice, its line items, the client record and the
 * VERIFIED payment reports. PDFs are never stored (Render's filesystem is ephemeral, and regenerating keeps the
 * document consistent with the current invoice state); see DECISIONS.md "Invoice PDFs".
 * <p>
 * Ownership: a CLIENT only gets invoices of the client its user account belongs to; another client's invoice is
 * reported as not found (404), so invoice ids cannot be probed.
 */
@Service
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final InvoicePaymentReportRepository paymentReportRepository;
    private final ClientRepository clientRepository;
    private final AuthService authService;
    private final InvoicePdfRenderer renderer;

    public InvoicePdfService(InvoiceRepository invoiceRepository, InvoiceLineItemRepository lineItemRepository,
                             InvoicePaymentReportRepository paymentReportRepository,
                             ClientRepository clientRepository, AuthService authService,
                             InvoicePdfRenderer renderer) {
        this.invoiceRepository = invoiceRepository;
        this.lineItemRepository = lineItemRepository;
        this.paymentReportRepository = paymentReportRepository;
        this.clientRepository = clientRepository;
        this.authService = authService;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public InvoicePdf renderForAdmin(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));
        return render(invoice);
    }

    @Transactional(readOnly = true)
    public InvoicePdf renderForClient(UUID invoiceId, String userId) {
        User user = authService.getUserById(UUID.fromString(userId));
        if (user.getClientId() == null) {
            throw new IllegalArgumentException("User is not associated with a client");
        }
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(candidate -> user.getClientId().equals(candidate.getClientId()))
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));
        return render(invoice);
    }

    private InvoicePdf render(Invoice invoice) {
        Client client = clientRepository.findById(invoice.getClientId()).orElse(null);
        BigDecimal verified = paymentReportRepository.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).stream()
                .filter(report -> report.getStatus() == PaymentReportStatus.VERIFIED)
                .filter(report -> report.getCurrency() == invoice.getCurrency())
                .map(InvoicePaymentReport::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        byte[] content = renderer.render(new InvoicePdfData(invoice,
                lineItemRepository.findByInvoiceId(invoice.getId()), client, verified));
        return new InvoicePdf(filenameFor(invoice.getInvoiceNumber()), content);
    }

    /** {@code GrowthPrint-Invoice-<invoiceNumber>.pdf}; characters unsafe in a file name become '-'. */
    static String filenameFor(String invoiceNumber) {
        String safe = invoiceNumber == null ? "" : invoiceNumber.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        return "GrowthPrint-Invoice-" + safe + ".pdf";
    }
}
