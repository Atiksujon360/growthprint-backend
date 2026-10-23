package com.growthprint.backend.invoice.entity;

import jakarta.persistence.*;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "invoice_number", nullable = false, length = 50, unique = true)
    private String invoiceNumber;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "client_email", nullable = false, length = 200)
    private String clientEmail;

    @Column(name = "issue_date", nullable = false)
    private java.time.LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private java.time.LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercent;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private InvoiceCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Invoice() {
    }

    public Invoice(UUID id, String invoiceNumber, UUID clientId, String clientName, String clientEmail,
                   java.time.LocalDate issueDate, java.time.LocalDate dueDate, BigDecimal subtotal,
                   BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal totalAmount,
                   InvoiceCurrency currency, InvoiceStatus status, Instant paidAt, String notes,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.subtotal = subtotal;
        this.taxPercent = taxPercent;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = status;
        this.paidAt = paidAt;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public UUID getClientId() { return clientId; }
    public String getClientName() { return clientName; }
    public String getClientEmail() { return clientEmail; }
    public java.time.LocalDate getIssueDate() { return issueDate; }
    public java.time.LocalDate getDueDate() { return dueDate; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxPercent() { return taxPercent; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public InvoiceCurrency getCurrency() { return currency; }
    public InvoiceStatus getStatus() { return status; }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getPaidAt() { return paidAt; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
