package com.growthprint.backend.lead.service;

import com.growthprint.backend.lead.dto.CreateLeadCommand;
import com.growthprint.backend.lead.dto.LeadResponse;
import com.growthprint.backend.lead.entity.Lead;
import com.growthprint.backend.lead.entity.LeadEnums;
import com.growthprint.backend.lead.repository.LeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.growthprint.backend.lead.entity.LeadEnums.LeadStatus;

@Service
public class LeadService {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final LeadRepository leadRepository;
    private final Clock clock;

    @Autowired
    public LeadService(LeadRepository leadRepository) {
        this(leadRepository, Clock.systemUTC());
    }

    public LeadService(LeadRepository leadRepository, Clock clock) {
        this.leadRepository = leadRepository;
        this.clock = clock;
    }

    @Transactional
    public LeadResponse createFromInquiry(CreateLeadCommand command) {
        Instant receivedAt = clock.instant();
        Lead lead = new Lead(
                UUID.randomUUID(),
                referenceNumber(receivedAt),
                normalizeRequired(command.name()),
                normalizeEmail(command.businessEmail()),
                normalizeRequired(command.company()),
                normalizeOptional(command.website()),
                command.serviceCategory(),
                normalizeRequired(command.projectType()),
                normalizeOptional(command.budgetRange()),
                normalizeOptional(command.preferredTimeline()),
                normalizeRequired(command.projectDescription()),
                command.preferredContactMethod(),
                command.consent(),
                LeadStatus.RECEIVED,
                receivedAt);

        return LeadResponse.from(leadRepository.save(lead));
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> findAll() {
        return leadRepository.findAllByOrderByReceivedAtDesc().stream()
                .map(LeadResponse::from)
                .toList();
    }

    private String referenceNumber(Instant receivedAt) {
        return "GP-INQ-" + YEAR_FORMATTER.format(receivedAt) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeEmail(String value) {
        return normalizeRequired(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeRequired(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
