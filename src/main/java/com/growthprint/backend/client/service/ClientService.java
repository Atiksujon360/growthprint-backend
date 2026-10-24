package com.growthprint.backend.client.service;

import com.growthprint.backend.client.dto.ClientResponse;
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
    public ClientResponse update(UUID id, java.util.Map<String, Object> updates) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id));

        // Apply allowed updates using constructor pattern
        String name = updates.containsKey("name") ? (String) updates.get("name") : client.getName();
        String contactEmail = updates.containsKey("contactEmail") ? (String) updates.get("contactEmail") : client.getContactEmail();
        String phone = updates.containsKey("contactPhone") ? (String) updates.get("contactPhone") : client.getPhone();
        String notes = updates.containsKey("notes") ? (String) updates.get("notes") : client.getNotes();

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
}
