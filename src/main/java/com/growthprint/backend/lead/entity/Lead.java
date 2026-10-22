package com.growthprint.backend.lead.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import static com.growthprint.backend.lead.entity.LeadEnums.LeadStatus;
import static com.growthprint.backend.lead.entity.LeadEnums.PreferredContactMethod;
import static com.growthprint.backend.lead.entity.LeadEnums.ServiceCategory;

@Entity
@Table(name = "leads")
public class Lead {

    @Id
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true, length = 32)
    private String referenceNumber;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "business_email", nullable = false, length = 254)
    private String businessEmail;

    @Column(nullable = false, length = 160)
    private String company;

    @Column(length = 2048)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_category", nullable = false, length = 40)
    private ServiceCategory serviceCategory;

    @Column(name = "project_type", nullable = false, length = 160)
    private String projectType;

    @Column(name = "budget_range", length = 120)
    private String budgetRange;

    @Column(name = "preferred_timeline", length = 120)
    private String preferredTimeline;

    @Column(name = "project_description", nullable = false, columnDefinition = "text")
    private String projectDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", nullable = false, length = 20)
    private PreferredContactMethod preferredContactMethod;

    @Column(nullable = false)
    private boolean consent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected Lead() {
    }

    public Lead(UUID id, String referenceNumber, String name, String businessEmail, String company,
                String website, ServiceCategory serviceCategory, String projectType, String budgetRange,
                String preferredTimeline, String projectDescription, PreferredContactMethod preferredContactMethod,
                boolean consent, LeadStatus status, Instant receivedAt) {
        this.id = id;
        this.referenceNumber = referenceNumber;
        this.name = name;
        this.businessEmail = businessEmail;
        this.company = company;
        this.website = website;
        this.serviceCategory = serviceCategory;
        this.projectType = projectType;
        this.budgetRange = budgetRange;
        this.preferredTimeline = preferredTimeline;
        this.projectDescription = projectDescription;
        this.preferredContactMethod = preferredContactMethod;
        this.consent = consent;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public UUID getId() { return id; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getName() { return name; }
    public String getBusinessEmail() { return businessEmail; }
    public String getCompany() { return company; }
    public String getWebsite() { return website; }
    public ServiceCategory getServiceCategory() { return serviceCategory; }
    public String getProjectType() { return projectType; }
    public String getBudgetRange() { return budgetRange; }
    public String getPreferredTimeline() { return preferredTimeline; }
    public String getProjectDescription() { return projectDescription; }
    public PreferredContactMethod getPreferredContactMethod() { return preferredContactMethod; }
    public boolean isConsent() { return consent; }
    public LeadStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
}
