package com.growthprint.backend.invoice.entity;

/** How the client says they paid (V15 chk_payment_reports_method). */
public enum PaymentMethod {
    BANK_TRANSFER,
    CARD,
    CHECK,
    OTHER
}
