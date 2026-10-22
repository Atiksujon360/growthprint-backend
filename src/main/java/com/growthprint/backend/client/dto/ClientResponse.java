package com.growthprint.backend.client.dto;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;

import java.time.LocalDate;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        String legalName,
        String contactEmail,
        String phone,
        String website,
        ClientEnums.ClientStatus status,
        ClientEnums.RetainerTier retainerTier,
        Integer monthlyRetainerHours,
        Integer consumedHoursCurrentMonth,
        LocalDate contractStartDate,
        String accountManager,
        ClientEnums.BillingCurrency billingCurrency,
        String notes
) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getLegalName(),
                client.getContactEmail(),
                client.getPhone(),
                client.getWebsite(),
                client.getStatus(),
                client.getRetainerTier(),
                client.getMonthlyRetainerHours(),
                client.getConsumedHoursCurrentMonth(),
                client.getContractStartDate(),
                client.getAccountManager(),
                client.getBillingCurrency(),
                client.getNotes()
        );
    }
}
