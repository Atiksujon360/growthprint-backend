package com.growthprint.backend.proposal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.common.validation.EnumValues;
import com.growthprint.backend.project.entity.ServiceCategory;
import com.growthprint.backend.proposal.dto.*;
import com.growthprint.backend.proposal.entity.Proposal;
import com.growthprint.backend.proposal.entity.ProposalCurrency;
import com.growthprint.backend.proposal.entity.ProposalStatus;
import com.growthprint.backend.proposal.exception.ProposalExpiredException;
import com.growthprint.backend.proposal.exception.ProposalNotFoundException;
import com.growthprint.backend.proposal.repository.ProposalRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProposalService {

    /** Upper bound for number allocation; collisions beyond this indicate a data problem, not bad luck. */
    private static final int MAX_NUMBER_ATTEMPTS = 1_000;

    private final ProposalRepository proposalRepository;
    private final ClientRepository clientRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public ProposalService(ProposalRepository proposalRepository,
                          ClientRepository clientRepository,
                          AuthService authService,
                          ObjectMapper objectMapper) {
        this.proposalRepository = proposalRepository;
        this.clientRepository = clientRepository;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> findAll() {
        return proposalRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> findMyProposals(String userId) {
        UUID clientId = authService.getUserById(parseUuid(userId, "user ID")).getClientId();
        if (clientId == null) {
            throw new IllegalStateException("User is not associated with a client");
        }
        return proposalRepository.findByClientId(clientId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProposalResponse findById(UUID id) {
        return toResponse(load(id));
    }

    @Transactional
    public ProposalResponse create(CreateProposalCommand command) {
        UUID clientId = parseUuid(command.clientId(), "client ID");

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + command.clientId()));

        // Validate payment milestones
        validatePaymentMilestones(command.paymentMilestones(), command.totalPrice());

        Proposal proposal = new Proposal(
                UUID.randomUUID(),
                allocateProposalNumber(),
                client.getId(),
                client.getName(),
                command.title(),
                serviceCategory(command.serviceCategory()),
                command.scopeSummary(),
                toJson(command.deliverables()),
                toJson(nullToEmpty(command.exclusions())),
                command.totalPrice(),
                EnumValues.parse(ProposalCurrency.class, command.currency(), "currency"),
                toJson(command.paymentMilestones()),
                command.timelineDuration(),
                command.validUntil(),
                ProposalStatus.DRAFT,
                command.notes(),
                Instant.now(),
                Instant.now()
        );

        return toResponse(proposalRepository.save(proposal));
    }

    @Transactional
    public ProposalResponse update(UUID id, UpdateProposalCommand command) {
        Proposal proposal = load(id);

        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw new IllegalStateException("Only draft proposals can be updated");
        }

        // Validate payment milestones
        validatePaymentMilestones(command.paymentMilestones(), command.totalPrice());

        proposal.setTitle(command.title());
        proposal.setServiceCategory(serviceCategory(command.serviceCategory()));
        proposal.setScopeSummary(command.scopeSummary());
        proposal.setDeliverables(toJson(command.deliverables()));
        proposal.setExclusions(toJson(nullToEmpty(command.exclusions())));
        proposal.setTotalPrice(command.totalPrice());
        proposal.setCurrency(EnumValues.parse(ProposalCurrency.class, command.currency(), "currency"));
        proposal.setPaymentMilestones(toJson(command.paymentMilestones()));
        proposal.setTimelineDuration(command.timelineDuration());
        proposal.setValidUntil(command.validUntil());
        proposal.setNotes(command.notes());

        return toResponse(proposalRepository.save(proposal));
    }

    @Transactional
    public ProposalResponse publish(UUID id, PublishProposalCommand command) {
        Proposal proposal = load(id);

        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw new IllegalStateException("Only draft proposals can be published");
        }

        proposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        proposal.setValidUntil(command.validUntil());

        return toResponse(proposalRepository.save(proposal));
    }

    /**
     * Client accepts or declines a proposal sent for review.
     * <ul>
     *     <li>Unknown proposal: 404. Another client's proposal: 403 (checked before any state is revealed).</li>
     *     <li>Past validUntil: the proposal is persisted as EXPIRED and a 409 is returned. The EXPIRED update
     *     must survive the exception, hence {@code noRollbackFor} (previously it was silently rolled back).</li>
     * </ul>
     */
    @Transactional(noRollbackFor = ProposalExpiredException.class)
    public ProposalResponse recordDecision(UUID id, ProposalDecisionCommand command, String userId) {
        Proposal proposal = load(id);

        com.growthprint.backend.auth.entity.User user = authService.getUserById(parseUuid(userId, "user ID"));
        UUID clientId = user.getClientId();
        if (clientId == null || !clientId.equals(proposal.getClientId())) {
            throw new AccessDeniedException("User is not authorized to decide on this proposal");
        }

        if (proposal.getStatus() != ProposalStatus.SENT_FOR_REVIEW) {
            throw new IllegalStateException("Proposal is not awaiting client decision");
        }

        if (LocalDate.now(ZoneOffset.UTC).isAfter(proposal.getValidUntil())) {
            proposal.setStatus(ProposalStatus.EXPIRED);
            proposalRepository.save(proposal);
            throw new ProposalExpiredException();
        }

        String decision = command.decision().trim().toUpperCase(Locale.ROOT);
        if ("ACCEPT".equals(decision)) {
            proposal.setStatus(ProposalStatus.ACCEPTED);
            proposal.setAcceptedAt(Instant.now());
            proposal.setAcceptedBy(user.getEmail());
        } else if ("DECLINE".equals(decision)) {
            proposal.setStatus(ProposalStatus.DECLINED);
            proposal.setDeclinedAt(Instant.now());
            proposal.setDeclinedBy(user.getEmail());
        } else {
            throw new IllegalArgumentException("Invalid decision. Allowed values: ACCEPT, DECLINE");
        }

        return toResponse(proposalRepository.save(proposal));
    }

    private Proposal load(UUID id) {
        return proposalRepository.findById(id).orElseThrow(() -> new ProposalNotFoundException(id));
    }

    private void validatePaymentMilestones(List<CreateProposalCommand.PaymentMilestone> milestones, BigDecimal totalPrice) {
        if (milestones == null || milestones.isEmpty()) {
            throw new IllegalArgumentException("Payment milestones are required");
        }

        // Validate percentages sum to 100
        int totalPercentage = milestones.stream()
                .mapToInt(CreateProposalCommand.PaymentMilestone::percentage)
                .sum();

        if (totalPercentage != 100) {
            throw new IllegalArgumentException(
                    String.format("Payment milestone percentages must sum to 100, got %d", totalPercentage));
        }

        // Validate amounts sum to total_price
        BigDecimal totalAmount = milestones.stream()
                .map(CreateProposalCommand.PaymentMilestone::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(totalPrice) != 0) {
            throw new IllegalArgumentException(
                    String.format("Payment milestone amounts must sum to total price %s, got %s",
                            totalPrice, totalAmount));
        }

        // Validate all amounts are positive
        for (CreateProposalCommand.PaymentMilestone milestone : milestones) {
            if (milestone.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment milestone amounts must be positive");
            }
        }
    }

    /** proposals.service_category has a CHECK constraint; validate here for a clean 400 instead of a 500. */
    private static String serviceCategory(String value) {
        return EnumValues.parse(ServiceCategory.class, value, "serviceCategory").name();
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + " format");
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize proposal data", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return json == null ? null : objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read stored proposal data", e);
        }
    }

    private ProposalResponse toResponse(Proposal proposal) {
        return new ProposalResponse(
                proposal.getId().toString(),
                proposal.getProposalNumber(),
                proposal.getClientId().toString(),
                proposal.getClientName(),
                proposal.getTitle(),
                proposal.getServiceCategory(),
                proposal.getScopeSummary(),
                fromJson(proposal.getDeliverables(), new TypeReference<List<String>>() {}),
                fromJson(proposal.getExclusions(), new TypeReference<List<String>>() {}),
                proposal.getTotalPrice(),
                proposal.getCurrency().name(),
                fromJson(proposal.getPaymentMilestones(), new TypeReference<List<ProposalResponse.PaymentMilestone>>() {}),
                proposal.getTimelineDuration(),
                proposal.getValidUntil().toString(),
                proposal.getStatus().name(),
                proposal.getAcceptedAt() != null ? proposal.getAcceptedAt().toString() : null,
                proposal.getAcceptedBy(),
                proposal.getDeclinedAt() != null ? proposal.getDeclinedAt().toString() : null,
                proposal.getDeclinedBy(),
                proposal.getNotes(),
                proposal.getCreatedAt() != null ? proposal.getCreatedAt().toString() : null,
                proposal.getUpdatedAt() != null ? proposal.getUpdatedAt().toString() : null
        );
    }

    /**
     * Keeps the existing GP-PROP-{year}-{nnnn} format. Starts at count()+1 and moves forward past taken
     * numbers (the previous loop regenerated the same number forever). Concurrent creates can still race;
     * the UNIQUE constraint on proposal_number then turns the loser into a 409 via GlobalExceptionHandler.
     */
    private String allocateProposalNumber() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        long next = proposalRepository.count() + 1;
        for (int attempt = 0; attempt < MAX_NUMBER_ATTEMPTS; attempt++, next++) {
            String candidate = String.format(Locale.ROOT, "GP-PROP-%d-%04d", year, next);
            if (!proposalRepository.existsByProposalNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a proposal number");
    }
}
