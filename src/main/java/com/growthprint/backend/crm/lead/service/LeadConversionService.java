package com.growthprint.backend.crm.lead.service;

import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.client.dto.ClientResponse;
import com.growthprint.backend.client.dto.CreateClientCommand;
import com.growthprint.backend.client.service.ClientService;
import com.growthprint.backend.crm.lead.dto.ConvertLeadToClientCommand;
import com.growthprint.backend.crm.lead.entity.CrmLead;
import com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadPipelineStage;
import com.growthprint.backend.crm.lead.exception.CrmLeadNotFoundException;
import com.growthprint.backend.crm.lead.repository.CrmLeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Converts a CRM lead into a client in ONE transaction: lock the lead, check eligibility and that it was not
 * converted before, create the client, mark the lead WON with the client id. Either both happen or neither.
 * Duplicate conversions are rejected by the row lock (409) and, as a last line, by the unique index from V23.
 * <p>
 * Authorization (controller): ADMIN, or a team member holding BOTH the CRM and CLIENTS modules. MVP1 data is
 * still owned by the default GrowthPrint business; this becomes business-scoped in M2.
 */
@Service
public class LeadConversionService {

    private static final Logger activity = LoggerFactory.getLogger("BUSINESS_ACTIVITY");

    private final CrmLeadRepository crmLeadRepository;
    private final ClientService clientService;
    private final UserRepository userRepository;
    private final Clock clock;

    public LeadConversionService(CrmLeadRepository crmLeadRepository, ClientService clientService,
                                 UserRepository userRepository, Clock clock) {
        this.crmLeadRepository = crmLeadRepository;
        this.clientService = clientService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public ClientResponse convert(UUID leadId, ConvertLeadToClientCommand command, UUID actorId) {
        CrmLead lead = crmLeadRepository.findForUpdateById(leadId)
                .orElseThrow(() -> new CrmLeadNotFoundException(leadId));
        if (lead.isConverted()) {
            throw new IllegalStateException("This lead has already been converted to a client");
        }
        if (Boolean.TRUE.equals(lead.getDoNotContact())
                || lead.getPipelineStage() == CrmLeadPipelineStage.LOST
                || lead.getPipelineStage() == CrmLeadPipelineStage.DO_NOT_CONTACT) {
            throw new IllegalStateException("Lost or do-not-contact leads cannot be converted to clients");
        }
        if (lead.getContactEmail() == null || lead.getContactEmail().isBlank()) {
            throw new IllegalArgumentException("The lead needs a contact email before it can become a client");
        }

        ConvertLeadToClientCommand c = command == null
                ? new ConvertLeadToClientCommand(null, null, null, null, null, null) : command;
        ClientResponse client = clientService.create(new CreateClientCommand(
                lead.getCompanyName(), c.legalName(), lead.getContactEmail(), null, lead.getWebsite(),
                c.billingCurrency(), c.retainerTier(), c.monthlyRetainerHours(), c.contractStartDate(),
                c.accountManager(), null), accountManagerName(actorId));

        lead.markConverted(client.id(), clock.instant());
        crmLeadRepository.save(lead);
        activity.info("event=LEAD_CONVERTED actor={} lead={} client={}", actorId, leadId, client.id());
        return client;
    }

    private String accountManagerName(UUID actorId) {
        return userRepository.findById(actorId)
                .map(u -> u.getFullName() == null || u.getFullName().isBlank() ? "GrowthPrint" : u.getFullName())
                .orElse("GrowthPrint");
    }
}
