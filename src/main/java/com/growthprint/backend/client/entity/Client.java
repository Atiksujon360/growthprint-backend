package com.growthprint.backend.client.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

import static com.growthprint.backend.client.entity.ClientEnums.BillingCurrency;
import static com.growthprint.backend.client.entity.ClientEnums.ClientStatus;
import static com.growthprint.backend.client.entity.ClientEnums.RetainerTier;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "legal_name", nullable = false, length = 160)
    private String legalName;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(length = 50)
    private String phone;

    @Column(nullable = false, length = 2048)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "retainer_tier", length = 50)
    private RetainerTier retainerTier;

    @Column(name = "monthly_retainer_hours")
    private Integer monthlyRetainerHours;

    @Column(name = "consumed_hours_current_month")
    private Integer consumedHoursCurrentMonth;

    @Column(name = "contract_start_date", nullable = false)
    private LocalDate contractStartDate;

    @Column(name = "account_manager", nullable = false, length = 120)
    private String accountManager;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_currency", nullable = false, length = 3)
    private BillingCurrency billingCurrency;

    @Column(columnDefinition = "text")
    private String notes;

    protected Client() {
    }

    public Client(UUID id, String name, String legalName, String contactEmail, String phone, String website,
                  ClientStatus status, RetainerTier retainerTier, Integer monthlyRetainerHours,
                  Integer consumedHoursCurrentMonth, LocalDate contractStartDate, String accountManager,
                  BillingCurrency billingCurrency, String notes) {
        this.id = id;
        this.name = name;
        this.legalName = legalName;
        this.contactEmail = contactEmail;
        this.phone = phone;
        this.website = website;
        this.status = status;
        this.retainerTier = retainerTier;
        this.monthlyRetainerHours = monthlyRetainerHours;
        this.consumedHoursCurrentMonth = consumedHoursCurrentMonth;
        this.contractStartDate = contractStartDate;
        this.accountManager = accountManager;
        this.billingCurrency = billingCurrency;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getContactEmail() { return contactEmail; }
    public String getPhone() { return phone; }
    public String getWebsite() { return website; }
    public ClientStatus getStatus() { return status; }
    public RetainerTier getRetainerTier() { return retainerTier; }
    public Integer getMonthlyRetainerHours() { return monthlyRetainerHours; }
    public Integer getConsumedHoursCurrentMonth() { return consumedHoursCurrentMonth; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public String getAccountManager() { return accountManager; }
    public BillingCurrency getBillingCurrency() { return billingCurrency; }
    public String getNotes() { return notes; }
}
