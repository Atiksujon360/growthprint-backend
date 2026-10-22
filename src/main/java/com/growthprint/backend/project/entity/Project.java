package com.growthprint.backend.project.entity;

import jakarta.persistence.*;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50, unique = true)
    private String reference;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "service_category", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ServiceCategory serviceCategory;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProjectStatus status;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "target_completion_date", nullable = false)
    private LocalDate targetCompletionDate;

    @Column(name = "budget_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetTotal;

    @Column(name = "budget_spent", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetSpent;

    @Column(name = "assigned_lead", length = 200)
    private String assignedLead;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(UUID id, String reference, String name, UUID clientId, ServiceCategory serviceCategory,
                   String description, ProjectStatus status, Integer progressPercent,
                   LocalDate startDate, LocalDate targetCompletionDate, BigDecimal budgetTotal,
                   BigDecimal budgetSpent, String assignedLead, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.reference = reference;
        this.name = name;
        this.clientId = clientId;
        this.serviceCategory = serviceCategory;
        this.description = description;
        this.status = status;
        this.progressPercent = progressPercent;
        this.startDate = startDate;
        this.targetCompletionDate = targetCompletionDate;
        this.budgetTotal = budgetTotal;
        this.budgetSpent = budgetSpent;
        this.assignedLead = assignedLead;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public String getName() { return name; }
    public UUID getClientId() { return clientId; }
    public ServiceCategory getServiceCategory() { return serviceCategory; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }
    public Integer getProgressPercent() { return progressPercent; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getTargetCompletionDate() { return targetCompletionDate; }
    public BigDecimal getBudgetTotal() { return budgetTotal; }
    public BigDecimal getBudgetSpent() { return budgetSpent; }
    public String getAssignedLead() { return assignedLead; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
