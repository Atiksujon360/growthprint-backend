package com.growthprint.backend.crm.lead.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

import static com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadPipelineStage;
import static com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadServiceCategory;
import static com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadSource;

@Entity
@Table(name = "crm_leads",
    uniqueConstraints = @UniqueConstraint(columnNames = {"company_name", "contact_email"}, name = "uk_crm_lead_company_email"),
    indexes = {
        @Index(name = "idx_crm_leads_pipeline_stage", columnList = "pipeline_stage"),
        @Index(name = "idx_crm_leads_qualification_score", columnList = "qualification_score"),
        @Index(name = "idx_crm_leads_source", columnList = "source"),
        @Index(name = "idx_crm_leads_inquiry_id", columnList = "inquiry_id")
    }
)
public class CrmLead {

    @Id
    private UUID id;

    @Column(name = "company_name", nullable = false, length = 160)
    private String companyName;

    @Column(nullable = false, length = 2048)
    private String website;

    @Column(nullable = false, length = 120)
    private String industry;

    @Column(nullable = false, length = 120)
    private String location;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "contact_title", length = 120)
    private String contactTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CrmLeadSource source;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "discovered_date", nullable = false)
    private Instant discoveredDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_service", nullable = false, length = 40)
    private CrmLeadServiceCategory targetService;

    @Column(name = "identified_problem", nullable = false, columnDefinition = "text")
    private String identifiedProblem;

    @Column(name = "research_summary", nullable = false, columnDefinition = "text")
    private String researchSummary;

    @Column(name = "qualification_score", nullable = false)
    private Integer qualificationScore;

    @Column(name = "business_fit_score")
    private Integer businessFitScore;

    @Column(name = "evidence_of_need_score")
    private Integer evidenceOfNeedScore;

    @Column(name = "contactability_score")
    private Integer contactabilityScore;

    @Column(name = "service_relevance_score")
    private Integer serviceRelevanceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_stage", nullable = false, length = 30)
    private CrmLeadPipelineStage pipelineStage;

    @Column(name = "last_contact_date")
    private Instant lastContactDate;

    @Column(name = "next_action", nullable = false, length = 500)
    private String nextAction;

    @Column(name = "do_not_contact", nullable = false)
    private Boolean doNotContact;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "inquiry_id")
    private UUID inquiryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CrmLead() {
    }

    public CrmLead(UUID id, String companyName, String website, String industry, String location,
                  String contactName, String contactEmail, String contactTitle, CrmLeadSource source,
                  String sourceUrl, Instant discoveredDate, CrmLeadServiceCategory targetService,
                  String identifiedProblem, String researchSummary, Integer qualificationScore,
                  Integer businessFitScore, Integer evidenceOfNeedScore, Integer contactabilityScore,
                  Integer serviceRelevanceScore, CrmLeadPipelineStage pipelineStage, Instant lastContactDate,
                  String nextAction, Boolean doNotContact, String notes, UUID inquiryId,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyName = companyName;
        this.website = website;
        this.industry = industry;
        this.location = location;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactTitle = contactTitle;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.discoveredDate = discoveredDate;
        this.targetService = targetService;
        this.identifiedProblem = identifiedProblem;
        this.researchSummary = researchSummary;
        this.qualificationScore = qualificationScore;
        this.businessFitScore = businessFitScore;
        this.evidenceOfNeedScore = evidenceOfNeedScore;
        this.contactabilityScore = contactabilityScore;
        this.serviceRelevanceScore = serviceRelevanceScore;
        this.pipelineStage = pipelineStage;
        this.lastContactDate = lastContactDate;
        this.nextAction = nextAction;
        this.doNotContact = doNotContact;
        this.notes = notes;
        this.inquiryId = inquiryId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getWebsite() { return website; }
    public String getIndustry() { return industry; }
    public String getLocation() { return location; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactTitle() { return contactTitle; }
    public CrmLeadSource getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public Instant getDiscoveredDate() { return discoveredDate; }
    public CrmLeadServiceCategory getTargetService() { return targetService; }
    public String getIdentifiedProblem() { return identifiedProblem; }
    public String getResearchSummary() { return researchSummary; }
    public Integer getQualificationScore() { return qualificationScore; }
    public Integer getBusinessFitScore() { return businessFitScore; }
    public Integer getEvidenceOfNeedScore() { return evidenceOfNeedScore; }
    public Integer getContactabilityScore() { return contactabilityScore; }
    public Integer getServiceRelevanceScore() { return serviceRelevanceScore; }
    public CrmLeadPipelineStage getPipelineStage() { return pipelineStage; }
    public Instant getLastContactDate() { return lastContactDate; }
    public String getNextAction() { return nextAction; }
    public Boolean getDoNotContact() { return doNotContact; }
    public String getNotes() { return notes; }
    public UUID getInquiryId() { return inquiryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
