package com.growthprint.backend.crm.lead.service;

import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.client.dto.ClientResponse;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.client.service.ClientService;
import com.growthprint.backend.crm.lead.dto.ConvertLeadToClientCommand;
import com.growthprint.backend.crm.lead.entity.CrmLead;
import com.growthprint.backend.crm.lead.entity.CrmLeadEnums;
import com.growthprint.backend.crm.lead.repository.CrmLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadConversionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private CrmLeadRepository leads;
    private ClientRepository clients;
    private LeadConversionService service;

    @BeforeEach
    void setUp() {
        leads = mock(CrmLeadRepository.class);
        clients = mock(ClientRepository.class);
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));
        UserRepository users = mock(UserRepository.class);
        when(users.findById(any())).thenReturn(Optional.empty());
        service = new LeadConversionService(leads, new ClientService(clients), users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CrmLead lead(CrmLeadEnums.CrmLeadPipelineStage stage, boolean doNotContact) {
        CrmLead lead = new CrmLead(UUID.randomUUID(), "Acme Ltd", "https://acme.test", "Retail", "KL", "Olive",
                "olive@acme.test", "CEO", CrmLeadEnums.CrmLeadSource.INBOUND_INQUIRY, null, NOW,
                CrmLeadEnums.CrmLeadServiceCategory.SOFTWARE_DEV, "Portal", "Inbound", 70, null, null, null, null,
                stage, null, "Send proposal", doNotContact, null, null, NOW, NOW);
        when(leads.findForUpdateById(lead.getId())).thenReturn(Optional.of(lead));
        return lead;
    }

    @Test
    void convertsOnceTransactionallyAndMarksTheLeadWon() {
        CrmLead lead = lead(CrmLeadEnums.CrmLeadPipelineStage.QUALIFIED, false);

        ClientResponse client = service.convert(lead.getId(),
                new ConvertLeadToClientCommand(null, ClientEnums.BillingCurrency.EUR, null, null, null, null),
                UUID.randomUUID());

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(saved.capture());
        assertThat(client.name()).isEqualTo("Acme Ltd");
        assertThat(client.legalName()).isEqualTo("Acme Ltd");
        assertThat(client.contactEmail()).isEqualTo("olive@acme.test");
        assertThat(client.billingCurrency()).isEqualTo(ClientEnums.BillingCurrency.EUR);
        assertThat(client.status()).isEqualTo(ClientEnums.ClientStatus.ACTIVE);
        assertThat(lead.getConvertedClientId()).isEqualTo(client.id());
        assertThat(lead.getPipelineStage()).isEqualTo(CrmLeadEnums.CrmLeadPipelineStage.WON);

        assertThatThrownBy(() -> service.convert(lead.getId(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lostOrDoNotContactLeadsAreNotEligible() {
        CrmLead lost = lead(CrmLeadEnums.CrmLeadPipelineStage.LOST, false);
        CrmLead dnc = lead(CrmLeadEnums.CrmLeadPipelineStage.QUALIFIED, true);
        assertThatThrownBy(() -> service.convert(lost.getId(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.convert(dnc.getId(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        verify(clients, never()).save(any());
    }

    @Test
    void pipelineUpdatesKeepTheConversionRecord() {
        CrmLead original = lead(CrmLeadEnums.CrmLeadPipelineStage.WON, false);
        UUID clientId = UUID.randomUUID();
        original.markConverted(clientId, NOW);
        CrmLead rebuilt = new CrmLead(original.getId(), "Acme Ltd", null, null, null, null, "olive@acme.test", null,
                CrmLeadEnums.CrmLeadSource.INBOUND_INQUIRY, null, NOW, CrmLeadEnums.CrmLeadServiceCategory.SOFTWARE_DEV,
                null, null, null, null, null, null, null, CrmLeadEnums.CrmLeadPipelineStage.REPLIED, null, null, false,
                null, null, NOW, NOW);
        rebuilt.carryConversionFrom(original);
        assertThat(rebuilt.getConvertedClientId()).isEqualTo(clientId);
        assertThat(rebuilt.getConvertedAt()).isEqualTo(NOW);
    }
}
