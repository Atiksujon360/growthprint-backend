package com.growthprint.backend.client.service;

import com.growthprint.backend.client.dto.ClientResponse;
import com.growthprint.backend.client.dto.UpdateClientCommand;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.exception.ClientNotFoundException;
import com.growthprint.backend.client.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

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
