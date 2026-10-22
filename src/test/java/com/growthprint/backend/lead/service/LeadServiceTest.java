package com.growthprint.backend.lead.service;

import com.growthprint.backend.lead.dto.CreateLeadCommand;
import com.growthprint.backend.lead.dto.LeadResponse;
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
import java.util.UUID;

import static com.growthprint.backend.lead.entity.LeadEnums.PreferredContactMethod.EMAIL;
import static com.growthprint.backend.lead.entity.LeadEnums.ServiceCategory.SOFTWARE_DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Test
    void createFromInquiryNormalizesFieldsAndPersistsReceivedLead() {
        Instant now = Instant.parse("2026-10-21T10:00:00Z");
        LeadService service = new LeadService(leadRepository, Clock.fixed(now, ZoneOffset.UTC));
        CreateLeadCommand command = new CreateLeadCommand(
                "  Alex   Morgan ", "Alex@Company.COM ", " Example   Company ", "",
                SOFTWARE_DEV, " Web Application Development ", " $10,000 - $25,000 ", null,
                " We need a customer portal for operations. ", EMAIL, true);
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadResponse response = service.createFromInquiry(command);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Alex Morgan");
        assertThat(saved.getBusinessEmail()).isEqualTo("alex@company.com");
        assertThat(saved.getCompany()).isEqualTo("Example Company");
        assertThat(saved.getWebsite()).isNull();
        assertThat(saved.getReceivedAt()).isEqualTo(now);
        assertThat(saved.getReferenceNumber()).matches("GP-INQ-2026-[A-F0-9]{8}");
        assertThat(response.status()).isEqualTo(LeadEnums.LeadStatus.RECEIVED);
    }
}
