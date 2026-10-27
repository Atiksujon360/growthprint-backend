package com.growthprint.backend.outreach.entity;

import com.growthprint.backend.project.entity.ServiceCategory;
import jakarta.persistence.*;
import jakarta.persistence.PreUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outreach_drafts")
public class OutreachDraft {

    @Id
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "recipient_email", nullable = false, length = 200)
    private String recipientEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "message_body", nullable = false, columnDefinition = "text")
    private String messageBody;

    @Column(name = "personalization_evidence", columnDefinition = "text")
    private String personalizationEvidence;

    @Column(name = "relevant_service", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ServiceCategory relevantService;

    @JdbcTypeCode(SqlTypes.JSON)
    private String riskFlags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutreachDraftStatus status;

    @Column(name = "approved_by_founder")
    private Boolean approvedByFounder;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutreachDraft() {
    }

    public OutreachDraft(UUID id, UUID leadId, String companyName, String recipientName, String recipientEmail,
                         String subject, String messageBody, String personalizationEvidence,
                         ServiceCategory relevantService, String riskFlags, OutreachDraftStatus status,
                         Boolean approvedByFounder, Instant approvedAt, Instant sentAt,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.leadId = leadId;
        this.companyName = companyName;
        this.recipientName = recipientName;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.messageBody = messageBody;
        this.personalizationEvidence = personalizationEvidence;
        this.relevantService = relevantService;
        this.riskFlags = riskFlags != null ? riskFlags : "[]";
        this.status = status;
        this.approvedByFounder = approvedByFounder;
        this.approvedAt = approvedAt;
        this.sentAt = sentAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getLeadId() { return leadId; }
    public String getCompanyName() { return companyName; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSubject() { return subject; }
    public String getMessageBody() { return messageBody; }
    public String getPersonalizationEvidence() { return personalizationEvidence; }
    public ServiceCategory getRelevantService() { return relevantService; }
    public String getRiskFlags() { return riskFlags; }
    public OutreachDraftStatus getStatus() { return status; }

    public void setStatus(OutreachDraftStatus status) {
        this.status = status;
    }

    public void setApprovedByFounder(Boolean approvedByFounder) {
        this.approvedByFounder = approvedByFounder;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Boolean getApprovedByFounder() { return approvedByFounder; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
