package com.growthprint.backend.crm.lead.service;

import com.growthprint.backend.crm.lead.dto.CreateCrmLeadCommand;
import com.growthprint.backend.crm.lead.dto.UpdatePipelineStageCommand;
import com.growthprint.backend.crm.lead.entity.CrmLead;
import com.growthprint.backend.crm.lead.exception.CrmLeadAlreadyExistsException;
import com.growthprint.backend.crm.lead.exception.CrmLeadConversionException;
import com.growthprint.backend.crm.lead.exception.CrmLeadNotFoundException;
import com.growthprint.backend.crm.lead.exception.InquiryNotFoundException;
import com.growthprint.backend.crm.lead.repository.CrmLeadRepository;
import com.growthprint.backend.lead.entity.Lead;
import com.growthprint.backend.lead.entity.LeadEnums;
import com.growthprint.backend.lead.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadPipelineStage;
import static com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadServiceCategory;
import static com.growthprint.backend.crm.lead.entity.CrmLeadEnums.CrmLeadSource;
import static com.growthprint.backend.lead.entity.LeadEnums.PreferredContactMethod;
import static com.growthprint.backend.lead.entity.LeadEnums.ServiceCategory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmLeadServiceTest {

    @Mock
    private CrmLeadRepository crmLeadRepository;

    @Mock
    private LeadRepository leadRepository;

    @Test
    void createPersistsCrmLeadWithValidation() {
        Instant now = Instant.parse("2026-10-23T10:00:00Z");
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.fixed(now, ZoneOffset.UTC));

        CreateCrmLeadCommand command = new CreateCrmLeadCommand(
                "Tech Corp", "https://techcorp.com", "Technology", "San Francisco, CA",
                "John Doe", "john@techcorp.com", "CTO", CrmLeadSource.PUBLIC_DIRECTORY,
                "https://directory.example.com/techcorp", CrmLeadServiceCategory.SOFTWARE_DEV,
                "Need custom platform", "Research completed", 85, 22, 21, 20, 22,
                CrmLeadPipelineStage.QUALIFIED, "Schedule discovery call", false, "Warm lead", null
        );

        when(crmLeadRepository.save(any(CrmLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(command);

        ArgumentCaptor<CrmLead> captor = ArgumentCaptor.forClass(CrmLead.class);
        verify(crmLeadRepository).save(captor.capture());
        CrmLead saved = captor.getValue();

        assertThat(saved.getCompanyName()).isEqualTo("Tech Corp");
        assertThat(saved.getQualificationScore()).isEqualTo(85);
        assertThat(saved.getPipelineStage()).isEqualTo(CrmLeadPipelineStage.QUALIFIED);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void convertFromInquiryFailsWhenCrmLeadAlreadyExists() {
        UUID inquiryId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-23T10:00:00Z");
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.fixed(now, ZoneOffset.UTC));

        CrmLead existingLead = new CrmLead(
                UUID.randomUUID(), "Tech Corp", "https://techcorp.com", "Technology", "San Francisco, CA",
                "John Doe", "john@techcorp.com", "CTO", CrmLeadSource.INBOUND_INQUIRY,
                null, now, CrmLeadServiceCategory.SOFTWARE_DEV,
                "Need custom platform", "Research completed", 85, 22, 21, 20, 22,
                CrmLeadPipelineStage.QUALIFIED, null, "Schedule discovery call", false, "Warm lead",
                inquiryId, now, now
        );
        when(crmLeadRepository.findByInquiryId(inquiryId)).thenReturn(Optional.of(existingLead));

        CreateCrmLeadCommand command = new CreateCrmLeadCommand(
                "Tech Corp", "https://techcorp.com", "Technology", "San Francisco, CA",
                "John Doe", "john@techcorp.com", "CTO", CrmLeadSource.INBOUND_INQUIRY,
                null, CrmLeadServiceCategory.SOFTWARE_DEV,
                "Need custom platform", "Research completed", 85, 22, 21, 20, 22,
                CrmLeadPipelineStage.QUALIFIED, "Schedule discovery call", false, "Warm lead", inquiryId
        );

        assertThatThrownBy(() -> service.convertFromInquiry(inquiryId, command))
                .isInstanceOf(CrmLeadAlreadyExistsException.class)
                .hasMessageContaining("CRM lead already exists for inquiry");
    }

    @Test
    void convertFromInquiryFailsWhenInquiryNotFound() {
        UUID inquiryId = UUID.randomUUID();
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.systemUTC());

        when(crmLeadRepository.findByInquiryId(inquiryId)).thenReturn(Optional.empty());
        when(leadRepository.findById(inquiryId)).thenReturn(Optional.empty());

        CreateCrmLeadCommand command = new CreateCrmLeadCommand(
                "Tech Corp", "https://techcorp.com", "Technology", "San Francisco, CA",
                "John Doe", "john@techcorp.com", "CTO", CrmLeadSource.INBOUND_INQUIRY,
                null, CrmLeadServiceCategory.SOFTWARE_DEV,
                "Need custom platform", "Research completed", 85, 22, 21, 20, 22,
                CrmLeadPipelineStage.QUALIFIED, "Schedule discovery call", false, "Warm lead", inquiryId
        );

        assertThatThrownBy(() -> service.convertFromInquiry(inquiryId, command))
                .isInstanceOf(InquiryNotFoundException.class)
                .hasMessageContaining("Inquiry not found");
    }

    @Test
    void convertFromInquiryFailsWhenInquiryLacksConsent() {
        UUID inquiryId = UUID.randomUUID();
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.systemUTC());

        when(crmLeadRepository.findByInquiryId(inquiryId)).thenReturn(Optional.empty());
        Lead inquiry = new Lead(
                inquiryId, "GP-INQ-2026-ABC123", "John Doe", "john@example.com",
                "Tech Corp", "https://techcorp.com", ServiceCategory.SOFTWARE_DEV,
                "Web App", "$10k-$25k", "3 months", "Need platform",
                PreferredContactMethod.EMAIL, false, LeadEnums.LeadStatus.RECEIVED, Instant.now()
        );
        when(leadRepository.findById(inquiryId)).thenReturn(Optional.of(inquiry));

        CreateCrmLeadCommand command = new CreateCrmLeadCommand(
                "Tech Corp", "https://techcorp.com", "Technology", "San Francisco, CA",
                "John Doe", "john@techcorp.com", "CTO", CrmLeadSource.INBOUND_INQUIRY,
                null, CrmLeadServiceCategory.SOFTWARE_DEV,
                "Need custom platform", "Research completed", 85, 22, 21, 20, 22,
                CrmLeadPipelineStage.QUALIFIED, "Schedule discovery call", false, "Warm lead", inquiryId
        );

        assertThatThrownBy(() -> service.convertFromInquiry(inquiryId, command))
                .isInstanceOf(CrmLeadConversionException.class)
                .hasMessageContaining("Cannot convert inquiry without consent");
    }

    @Test
    void updatePipelineStageUpdatesCrmLead() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-23T10:00:00Z");
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.fixed(now, ZoneOffset.UTC));

        CrmLead existingLead = new CrmLead(
                id, "Tech Corp", "https://techcorp.com", "Technology", "San Francisco, CA",
                "John Doe", "john@techcorp.com", "CTO", CrmLeadSource.PUBLIC_DIRECTORY,
                null, now, CrmLeadServiceCategory.SOFTWARE_DEV,
                "Need custom platform", "Research completed", 85, 22, 21, 20, 22,
                CrmLeadPipelineStage.QUALIFIED, null, "Schedule discovery call", false, "Warm lead",
                null, now.minusSeconds(3600), now.minusSeconds(3600)
        );

        when(crmLeadRepository.findById(id)).thenReturn(Optional.of(existingLead));
        when(crmLeadRepository.save(any(CrmLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePipelineStageCommand command = new UpdatePipelineStageCommand(CrmLeadPipelineStage.MEETING_SCHEDULED);
        service.updatePipelineStage(id, command);

        ArgumentCaptor<CrmLead> captor = ArgumentCaptor.forClass(CrmLead.class);
        verify(crmLeadRepository).save(captor.capture());
        CrmLead updated = captor.getValue();

        assertThat(updated.getPipelineStage()).isEqualTo(CrmLeadPipelineStage.MEETING_SCHEDULED);
        assertThat(updated.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void updatePipelineStageFailsWhenNotFound() {
        UUID id = UUID.randomUUID();
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.systemUTC());

        when(crmLeadRepository.findById(id)).thenReturn(Optional.empty());

        UpdatePipelineStageCommand command = new UpdatePipelineStageCommand(CrmLeadPipelineStage.MEETING_SCHEDULED);

        assertThatThrownBy(() -> service.updatePipelineStage(id, command))
                .isInstanceOf(CrmLeadNotFoundException.class)
                .hasMessageContaining("CRM lead not found");
    }

    @Test
    void deleteRemovesCrmLead() {
        UUID id = UUID.randomUUID();
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.systemUTC());

        CrmLead existingLead = new CrmLead(
                id, "Tech Corp", "https://techcorp.com", "Technology", "San Francisco, CA",
                "John Doe", "john@techcorp.com", "CTO", CrmLeadSource.PUBLIC_DIRECTORY,
                null, Instant.now(), CrmLeadServiceCategory.SOFTWARE_DEV,
                "Need custom platform", "Research completed", 85, 22, 21, 20, 22,
                CrmLeadPipelineStage.QUALIFIED, null, "Schedule discovery call", false, "Warm lead",
                null, Instant.now(), Instant.now()
        );

        when(crmLeadRepository.findById(id)).thenReturn(Optional.of(existingLead));

        service.delete(id);

        verify(crmLeadRepository).delete(existingLead);
    }

    @Test
    void deleteFailsWhenNotFound() {
        UUID id = UUID.randomUUID();
        CrmLeadService service = new CrmLeadService(crmLeadRepository, leadRepository, Clock.systemUTC());

        when(crmLeadRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(CrmLeadNotFoundException.class)
                .hasMessageContaining("CRM lead not found");
    }
}
