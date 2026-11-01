package com.growthprint.backend.invoice.entity;

/** Review state of a client payment report (V15 chk_payment_reports_status). */
public enum PaymentReportStatus {
    /** Submitted by the client, awaiting ADMIN review. The invoice is unchanged. */
    PENDING,
    /** ADMIN confirmed the payment; the invoice was marked PAID. */
    VERIFIED,
    /** ADMIN could not confirm the payment; the invoice is unchanged. */
    REJECTED
}
