package com.growthprint.backend.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A client's report that they paid an invoice (table from V15). Created PENDING; an ADMIN later verifies
 * (invoice becomes PAID) or rejects it. Reports are never deleted.
 */
@Entity
@Table(name = "invoice_payment_reports")
public class InvoicePaymentReport {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "reported_by_user_id", nullable = false)
    private UUID reportedByUserId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private InvoiceCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(length = 120)
    private String reference;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentReportStatus status;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InvoicePaymentReport() {
    }

    public InvoicePaymentReport(UUID id, UUID invoiceId, UUID clientId, UUID reportedByUserId, BigDecimal amount,
                                InvoiceCurrency currency, PaymentMethod paymentMethod, String reference,
                                LocalDate paidOn, String note, Instant createdAt) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.clientId = clientId;
        this.reportedByUserId = reportedByUserId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.reference = reference;
        this.paidOn = paidOn;
        this.note = note;
        this.status = PaymentReportStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Records the single, final ADMIN review decision. */
    public void review(PaymentReportStatus decision, UUID reviewerUserId, String reviewNote, Instant when) {
        this.status = decision;
        this.reviewedByUserId = reviewerUserId;
        this.reviewNote = reviewNote;
        this.reviewedAt = when;
        this.updatedAt = when;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getClientId() { return clientId; }
    public UUID getReportedByUserId() { return reportedByUserId; }
    public BigDecimal getAmount() { return amount; }
    public InvoiceCurrency getCurrency() { return currency; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getReference() { return reference; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getNote() { return note; }
    public PaymentReportStatus getStatus() { return status; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
