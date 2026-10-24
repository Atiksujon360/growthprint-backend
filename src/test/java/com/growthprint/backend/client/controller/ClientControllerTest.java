package com.growthprint.backend.client.controller;

import com.growthprint.backend.client.dto.ClientResponse;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.exception.ClientNotFoundException;
import com.growthprint.backend.client.service.ClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientControllerTest {

    @Mock
    private ClientService clientService;

    @Test
    void listReturnsAllClients() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ClientResponse response1 = new ClientResponse(
                id1, "Tech Corp", "Tech Corporation LLC", "contact@techcorp.com", "+1-555-0100",
                "https://techcorp.com", ClientEnums.ClientStatus.ACTIVE, ClientEnums.RetainerTier.PRO_GROWTH,
                40, 25, LocalDate.of(2024, 1, 15), "Jane Smith", ClientEnums.BillingCurrency.USD, null
        );

        ClientResponse response2 = new ClientResponse(
                id2, "Startup Inc", "Startup Inc.", "info@startup.io", null,
                "https://startup.io", ClientEnums.ClientStatus.INVITED, ClientEnums.RetainerTier.ESSENTIAL_MAINTENANCE,
                20, 10, LocalDate.of(2024, 3, 1), "John Doe", ClientEnums.BillingCurrency.EUR, "New client"
        );

        when(clientService.findAll()).thenReturn(List.of(response1, response2));

        ClientController controller = new ClientController(clientService);
        List<ClientResponse> result = controller.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(id1);
        assertThat(result.get(0).name()).isEqualTo("Tech Corp");
        assertThat(result.get(0).status()).isEqualTo(ClientEnums.ClientStatus.ACTIVE);
        assertThat(result.get(1).id()).isEqualTo(id2);
        assertThat(result.get(1).name()).isEqualTo("Startup Inc");
        assertThat(result.get(1).status()).isEqualTo(ClientEnums.ClientStatus.INVITED);
    }

    @Test
    void getByIdReturnsClient() {
        UUID id = UUID.randomUUID();

        ClientResponse response = new ClientResponse(
                id, "Tech Corp", "Tech Corporation LLC", "contact@techcorp.com", "+1-555-0100",
                "https://techcorp.com", ClientEnums.ClientStatus.ACTIVE, ClientEnums.RetainerTier.PRO_GROWTH,
                40, 25, LocalDate.of(2024, 1, 15), "Jane Smith", ClientEnums.BillingCurrency.USD, null
        );

        when(clientService.findById(id)).thenReturn(response);

        ClientController controller = new ClientController(clientService);
        ClientResponse result = controller.getById(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Tech Corp");
        assertThat(result.legalName()).isEqualTo("Tech Corporation LLC");
        assertThat(result.contactEmail()).isEqualTo("contact@techcorp.com");
        assertThat(result.phone()).isEqualTo("+1-555-0100");
        assertThat(result.website()).isEqualTo("https://techcorp.com");
        assertThat(result.status()).isEqualTo(ClientEnums.ClientStatus.ACTIVE);
        assertThat(result.retainerTier()).isEqualTo(ClientEnums.RetainerTier.PRO_GROWTH);
        assertThat(result.monthlyRetainerHours()).isEqualTo(40);
        assertThat(result.consumedHoursCurrentMonth()).isEqualTo(25);
        assertThat(result.accountManager()).isEqualTo("Jane Smith");
        assertThat(result.billingCurrency()).isEqualTo(ClientEnums.BillingCurrency.USD);
    }

    @Test
    void getByIdReturns404ForUnknownId() {
        UUID id = UUID.randomUUID();

        when(clientService.findById(id))
                .thenThrow(new ClientNotFoundException(id));

        ClientController controller = new ClientController(clientService);

        assertThatThrownBy(() -> controller.getById(id))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessageContaining("Client not found with id: " + id);
    }

    @Test
    void updateModifiesClient() {
        UUID id = UUID.randomUUID();

        ClientResponse response = new ClientResponse(
                id, "Updated Corp", "Updated Corporation LLC", "updated@corp.com", "+1-555-9999",
                "https://updated.com", ClientEnums.ClientStatus.ACTIVE, ClientEnums.RetainerTier.PRO_GROWTH,
                40, 25, LocalDate.of(2024, 1, 15), "Jane Smith", ClientEnums.BillingCurrency.USD, "Updated notes"
        );

        when(clientService.update(eq(id), any())).thenReturn(response);

        ClientController controller = new ClientController(clientService);
        ClientResponse result = controller.update(id, java.util.Map.of("name", "Updated Corp"));

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Updated Corp");
        assertThat(result.contactEmail()).isEqualTo("updated@corp.com");
        assertThat(result.notes()).isEqualTo("Updated notes");
    }
}
