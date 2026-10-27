package com.growthprint.backend.proposal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.proposal.dto.*;
import com.growthprint.backend.proposal.entity.Proposal;
import com.growthprint.backend.proposal.entity.ProposalCurrency;
import com.growthprint.backend.proposal.entity.ProposalStatus;
import com.growthprint.backend.proposal.repository.ProposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ProposalService {

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
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format: " + userId);
        }

        UUID clientId = authService.getUserById(userUuid).getClientId();
        if (clientId == null) {
            throw new IllegalStateException("User is not associated with a client");
        }
        return proposalRepository.findByClientId(clientId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProposalResponse findById(UUID id) {
        Proposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + id));
        return toResponse(proposal);
    }

    @Transactional
    public ProposalResponse create(CreateProposalCommand command) {
        UUID clientId;
        try {
            clientId = UUID.fromString(command.clientId());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid client ID format: " + command.clientId());
        }

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + command.clientId()));

        // Validate payment milestones
        validatePaymentMilestones(command.paymentMilestones(), command.totalPrice());

        String proposalNumber = generateProposalNumber();
        while (proposalRepository.existsByProposalNumber(proposalNumber)) {
            proposalNumber = generateProposalNumber();
        }

        Proposal proposal = new Proposal(
                UUID.randomUUID(),
                proposalNumber,
                client.getId(),
                client.getName(),
                command.title(),
                command.serviceCategory(),
                command.scopeSummary(),
                toJson(command.deliverables()),
                toJson(command.exclusions()),
                command.totalPrice(),
                ProposalCurrency.valueOf(command.currency()),
                toJson(command.paymentMilestones()),
                command.timelineDuration(),
                command.validUntil(),
                ProposalStatus.DRAFT,
                command.notes(),
                Instant.now(),
                Instant.now()
        );

        Proposal saved = proposalRepository.save(proposal);
        return toResponse(saved);
    }

    @Transactional
    public ProposalResponse update(UUID id, UpdateProposalCommand command) {
        Proposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + id));

        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw new IllegalStateException("Only draft proposals can be updated");
        }

        // Validate payment milestones
        validatePaymentMilestones(command.paymentMilestones(), command.totalPrice());

        proposal.setTitle(command.title());
        proposal.setServiceCategory(command.serviceCategory());
        proposal.setScopeSummary(command.scopeSummary());
        proposal.setDeliverables(toJson(command.deliverables()));
        proposal.setExclusions(toJson(command.exclusions()));
        proposal.setTotalPrice(command.totalPrice());
        proposal.setCurrency(ProposalCurrency.valueOf(command.currency()));
        proposal.setPaymentMilestones(toJson(command.paymentMilestones()));
        proposal.setTimelineDuration(command.timelineDuration());
        proposal.setValidUntil(command.validUntil());
        proposal.setNotes(command.notes());

        Proposal saved = proposalRepository.save(proposal);
        return toResponse(saved);
    }

    @Transactional
    public ProposalResponse publish(UUID id, PublishProposalCommand command) {
        Proposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + id));

        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw new IllegalStateException("Only draft proposals can be published");
        }

        proposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        proposal.setValidUntil(command.validUntil());

        Proposal saved = proposalRepository.save(proposal);
        return toResponse(saved);
    }

    @Transactional
    public ProposalResponse recordDecision(UUID id, ProposalDecisionCommand command, String userId) {
        Proposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + id));

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format: " + userId);
        }

        com.growthprint.backend.auth.entity.User user = authService.getUserById(userUuid);
        UUID clientId = user.getClientId();
        if (clientId == null || !clientId.equals(proposal.getClientId())) {
            throw new IllegalStateException("User is not authorized to decide on this proposal");
        }

        if (proposal.getStatus() != ProposalStatus.SENT_FOR_REVIEW) {
            throw new IllegalStateException("Proposal is not awaiting client decision");
        }

        if (LocalDate.now().isAfter(proposal.getValidUntil())) {
            proposal.setStatus(ProposalStatus.EXPIRED);
            proposalRepository.save(proposal);
            throw new IllegalStateException("Proposal has expired");
        }

        String decision = command.decision().toUpperCase();
        if ("ACCEPT".equals(decision)) {
            proposal.setStatus(ProposalStatus.ACCEPTED);
            proposal.setAcceptedAt(Instant.now());
            proposal.setAcceptedBy(user.getEmail());
        } else if ("DECLINE".equals(decision)) {
            proposal.setStatus(ProposalStatus.DECLINED);
            proposal.setDeclinedAt(Instant.now());
            proposal.setDeclinedBy(user.getEmail());
        } else {
            throw new IllegalArgumentException("Invalid decision: " + decision);
        }

        Proposal saved = proposalRepository.save(proposal);
        return toResponse(saved);
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
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
                proposal.getCreatedAt().toString(),
                proposal.getUpdatedAt().toString()
        );
    }

    private String generateProposalNumber() {
        int year = java.time.Year.now().getValue();
        long count = proposalRepository.count() + 1;
        return String.format("GP-PROP-%d-%04d", year, count);
    }
}
