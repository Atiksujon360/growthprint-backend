package com.growthprint.backend.proposal.entity;

import jakarta.persistence.*;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "proposals")
public class Proposal {

    @Id
    private UUID id;

    @Column(name = "proposal_number", nullable = false, length = 50, unique = true)
    private String proposalNumber;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "service_category", nullable = false, length = 50)
    private String serviceCategory;

    @Column(name = "scope_summary", nullable = false, columnDefinition = "text")
    private String scopeSummary;

    @Column(columnDefinition = "jsonb")
    private String deliverables;

    @Column(columnDefinition = "jsonb")
    private String exclusions;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private ProposalCurrency currency;

    @Column(name = "payment_milestones", columnDefinition = "jsonb")
    private String paymentMilestones;

    @Column(name = "timeline_duration", nullable = false, length = 100)
    private String timelineDuration;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProposalStatus status;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by", length = 200)
    private String acceptedBy;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "declined_by", length = 200)
    private String declinedBy;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Proposal() {
    }

    public Proposal(UUID id, String proposalNumber, UUID clientId, String clientName,
                   String title, String serviceCategory, String scopeSummary,
                   String deliverables, String exclusions, BigDecimal totalPrice,
                   ProposalCurrency currency, String paymentMilestones, String timelineDuration,
                   LocalDate validUntil, ProposalStatus status, String notes,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.proposalNumber = proposalNumber;
        this.clientId = clientId;
        this.clientName = clientName;
        this.title = title;
        this.serviceCategory = serviceCategory;
        this.scopeSummary = scopeSummary;
        this.deliverables = deliverables;
        this.exclusions = exclusions;
        this.totalPrice = totalPrice;
        this.currency = currency;
        this.paymentMilestones = paymentMilestones;
        this.timelineDuration = timelineDuration;
        this.validUntil = validUntil;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getProposalNumber() { return proposalNumber; }
    public UUID getClientId() { return clientId; }
    public String getClientName() { return clientName; }
    public String getTitle() { return title; }
    public String getServiceCategory() { return serviceCategory; }
    public String getScopeSummary() { return scopeSummary; }
    public String getDeliverables() { return deliverables; }
    public String getExclusions() { return exclusions; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public ProposalCurrency getCurrency() { return currency; }
    public String getPaymentMilestones() { return paymentMilestones; }
    public String getTimelineDuration() { return timelineDuration; }
    public LocalDate getValidUntil() { return validUntil; }
    public ProposalStatus getStatus() { return status; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public String getAcceptedBy() { return acceptedBy; }
    public Instant getDeclinedAt() { return declinedAt; }
    public String getDeclinedBy() { return declinedBy; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters for updates
    public void setTitle(String title) {
        this.title = title;
    }

    public void setServiceCategory(String serviceCategory) {
        this.serviceCategory = serviceCategory;
    }

    public void setScopeSummary(String scopeSummary) {
        this.scopeSummary = scopeSummary;
    }

    public void setDeliverables(String deliverables) {
        this.deliverables = deliverables;
    }

    public void setExclusions(String exclusions) {
        this.exclusions = exclusions;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setCurrency(ProposalCurrency currency) {
        this.currency = currency;
    }

    public void setPaymentMilestones(String paymentMilestones) {
        this.paymentMilestones = paymentMilestones;
    }

    public void setTimelineDuration(String timelineDuration) {
        this.timelineDuration = timelineDuration;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // Setters for status updates
    public void setStatus(ProposalStatus status) {
        this.status = status;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public void setAcceptedBy(String acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public void setDeclinedAt(Instant declinedAt) {
        this.declinedAt = declinedAt;
    }

    public void setDeclinedBy(String declinedBy) {
        this.declinedBy = declinedBy;
    }
}
