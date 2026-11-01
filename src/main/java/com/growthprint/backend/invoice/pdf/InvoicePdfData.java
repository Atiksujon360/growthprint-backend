package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceLineItem;
import com.growthprint.backend.invoice.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Everything an invoice PDF is rendered from: the persisted invoice (its stored subtotal/tax/total are
 * authoritative and are never recalculated), its line items, the client record (may be {@code null} if it is
 * not available) and the sum of VERIFIED payment reports.
 */
public record InvoicePdfData(Invoice invoice, List<InvoiceLineItem> lineItems, Client client,
                             BigDecimal verifiedPayments) {

    public InvoicePdfData {
        Objects.requireNonNull(invoice, "invoice");
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        verifiedPayments = verifiedPayments == null ? BigDecimal.ZERO : verifiedPayments;
    }

    /**
     * Amount shown as paid.
     * <ul>
     *     <li>PAID: the invoice total. The backend status is authoritative; an ADMIN can reconcile an invoice
     *     directly, without a client payment report.</li>
     *     <li>Otherwise: the sum of VERIFIED payment reports.</li>
     * </ul>
     */
    public BigDecimal amountPaid() {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return invoice.getTotalAmount().max(verifiedPayments);
        }
        return verifiedPayments;
    }

    /** Outstanding balance: 0 for PAID and VOID invoices, otherwise total minus paid (never negative). */
    public BigDecimal balanceDue() {
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID) {
            return BigDecimal.ZERO.setScale(2);
        }
        return invoice.getTotalAmount().subtract(verifiedPayments).max(BigDecimal.ZERO).setScale(2);
    }
}
