package com.growthprint.backend.client.service;

import com.growthprint.backend.client.dto.ClientResponse;
import com.growthprint.backend.client.dto.UpdateClientCommand;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.exception.ClientNotFoundException;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.client.dto.CreateClientCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    /** Creates an ACTIVE client. Used directly (POST /clients) and by lead conversion. */
    @Transactional
    public ClientResponse create(CreateClientCommand command, String defaultAccountManager) {
        com.growthprint.backend.client.entity.Client client = newClient(command, defaultAccountManager,
                java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        return ClientResponse.from(clientRepository.save(client));
    }

    static com.growthprint.backend.client.entity.Client newClient(CreateClientCommand c, String defaultAccountManager,
                                                                   java.time.LocalDate today) {
        String name = c.name().trim();
        return new com.growthprint.backend.client.entity.Client(
                UUID.randomUUID(),
                name,
                blankTo(c.legalName(), name),
                c.contactEmail().trim(),
                blankTo(c.phone(), null),
                blankTo(c.website(), ""),
                com.growthprint.backend.client.entity.ClientEnums.ClientStatus.ACTIVE,
                c.retainerTier(),
                c.monthlyRetainerHours(),
                c.monthlyRetainerHours() == null ? null : 0,
                c.contractStartDate() == null ? today : c.contractStartDate(),
                blankTo(c.accountManager(), defaultAccountManager),
                c.billingCurrency() == null ? com.growthprint.backend.client.entity.ClientEnums.BillingCurrency.USD
                        : c.billingCurrency(),
                blankTo(c.notes(), null));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> findAll() {
        return clientRepository.findAll().stream()
                .map(ClientResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id));
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse update(UUID id, UpdateClientCommand updates) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id));

        // Partial update: null/omitted fields keep their current value (validated by @Valid in the controller).
        String name = updates.name() != null ? updates.name().trim() : client.getName();
        String contactEmail = updates.contactEmail() != null ? updates.contactEmail().trim() : client.getContactEmail();
        String phone = updates.phone() != null ? blankToNull(updates.phone()) : client.getPhone();
        String notes = updates.notes() != null ? updates.notes() : client.getNotes();

        Client updatedClient = new Client(
                client.getId(),
                name,
                client.getLegalName(),
                contactEmail,
                phone,
                client.getWebsite(),
                client.getStatus(),
                client.getRetainerTier(),
                client.getMonthlyRetainerHours(),
                client.getConsumedHoursCurrentMonth(),
                client.getContractStartDate(),
                client.getAccountManager(),
                client.getBillingCurrency(),
                notes
        );

        Client savedClient = clientRepository.save(updatedClient);
        return ClientResponse.from(savedClient);
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
