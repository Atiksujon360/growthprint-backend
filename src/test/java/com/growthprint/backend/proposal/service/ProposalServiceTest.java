package com.growthprint.backend.proposal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.proposal.dto.*;
import com.growthprint.backend.proposal.entity.Proposal;
import com.growthprint.backend.proposal.entity.ProposalCurrency;
import com.growthprint.backend.proposal.entity.ProposalStatus;
import com.growthprint.backend.proposal.repository.ProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProposalService proposalService;

    private Client testClient;
    private User testUser;
    private Proposal testProposal;

    @BeforeEach
    void setUp() {
        testClient = new Client(
                UUID.randomUUID(),
                "Test Client",
                "Test Client LLC",
                "test@example.com",
                "+1-555-0100",
                "https://example.com",
                ClientEnums.ClientStatus.ACTIVE,
                ClientEnums.RetainerTier.PRO_GROWTH,
                40,
                25,
                LocalDate.of(2025, 1, 1),
                "Test Manager",
                ClientEnums.BillingCurrency.USD,
                "Test notes"
        );

        testUser = new User(
                UUID.randomUUID(),
                "user@example.com",
                "user@example.com",
                "encodedPassword",
                com.growthprint.backend.auth.entity.UserRole.CLIENT,
                com.growthprint.backend.auth.entity.AccountStatus.ACTIVE,
                testClient.getId(),
                Instant.now(),
                Instant.now()
        );

        testProposal = new Proposal(
                UUID.randomUUID(),
                "GP-PROP-2026-0001",
                testClient.getId(),
                testClient.getName(),
                "Test Proposal",
                "SOFTWARE_DEV",
                "Test scope summary",
                "[\"Deliverable 1\", \"Deliverable 2\"]",
                "[\"Exclusion 1\"]",
                new BigDecimal("10000.00"),
                ProposalCurrency.USD,
                "[{\"description\":\"Milestone 1\",\"percentage\":50,\"amount\":5000.00}]",
                "8 Weeks",
                LocalDate.of(2026, 12, 31),
                ProposalStatus.DRAFT,
                "Test notes",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void findAll_shouldReturnAllProposals() {
        when(proposalRepository.findAll()).thenReturn(List.of(testProposal));

        List<ProposalResponse> result = proposalService.findAll();

        assertEquals(1, result.size());
        assertEquals(testProposal.getId().toString(), result.get(0).id());
        verify(proposalRepository).findAll();
    }

    @Test
    void findMyProposals_shouldReturnClientProposals() {
        when(authService.getUserById(testUser.getId())).thenReturn(testUser);
        when(proposalRepository.findByClientId(testClient.getId())).thenReturn(List.of(testProposal));

        List<ProposalResponse> result = proposalService.findMyProposals(testUser.getId().toString());

        assertEquals(1, result.size());
        verify(proposalRepository).findByClientId(testClient.getId());
    }

    @Test
    void findById_shouldReturnProposal() {
        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));

        ProposalResponse result = proposalService.findById(testProposal.getId());

        assertNotNull(result);
        assertEquals(testProposal.getId().toString(), result.id());
        verify(proposalRepository).findById(testProposal.getId());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(proposalRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> proposalService.findById(id));
        verify(proposalRepository).findById(id);
    }

    @Test
    void create_shouldCreateNewProposal() {
        CreateProposalCommand command = new CreateProposalCommand(
                testClient.getId().toString(),
                "New Proposal",
                "SOFTWARE_DEV",
                "New scope summary",
                List.of("Deliverable 1"),
                List.of("Exclusion 1"),
                new BigDecimal("5000.00"),
                "USD",
                List.of(new CreateProposalCommand.PaymentMilestone("Milestone 1", 50, new BigDecimal("2500.00"))),
                "4 Weeks",
                LocalDate.of(2026, 12, 31),
                "Notes"
        );

        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(proposalRepository.existsByProposalNumber(anyString())).thenReturn(false);
        when(proposalRepository.save(any(Proposal.class))).thenReturn(testProposal);

        ProposalResponse result = proposalService.create(command);

        assertNotNull(result);
        verify(clientRepository).findById(testClient.getId());
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    void create_shouldThrowWhenClientNotFound() {
        CreateProposalCommand command = new CreateProposalCommand(
                UUID.randomUUID().toString(),
                "New Proposal",
                "SOFTWARE_DEV",
                "New scope summary",
                List.of("Deliverable 1"),
                List.of("Exclusion 1"),
                new BigDecimal("5000.00"),
                "USD",
                List.of(new CreateProposalCommand.PaymentMilestone("Milestone 1", 50, new BigDecimal("2500.00"))),
                "4 Weeks",
                LocalDate.of(2026, 12, 31),
                "Notes"
        );

        when(clientRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> proposalService.create(command));
    }

    @Test
    void update_shouldUpdateDraftProposal() {
        UpdateProposalCommand command = new UpdateProposalCommand(
                "Updated Title",
                "SOFTWARE_DEV",
                "Updated scope",
                List.of("Updated Deliverable"),
                List.of("Updated Exclusion"),
                new BigDecimal("15000.00"),
                "USD",
                List.of(new CreateProposalCommand.PaymentMilestone("Milestone 1", 50, new BigDecimal("7500.00"))),
                "6 Weeks",
                LocalDate.of(2026, 12, 31),
                "Updated notes"
        );

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));
        when(proposalRepository.save(any(Proposal.class))).thenReturn(testProposal);

        ProposalResponse result = proposalService.update(testProposal.getId(), command);

        assertNotNull(result);
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    void update_shouldThrowWhenNotDraft() {
        testProposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        UpdateProposalCommand command = new UpdateProposalCommand(
                "Updated Title",
                "SOFTWARE_DEV",
                "Updated scope",
                List.of("Updated Deliverable"),
                List.of("Updated Exclusion"),
                new BigDecimal("15000.00"),
                "USD",
                List.of(new CreateProposalCommand.PaymentMilestone("Milestone 1", 50, new BigDecimal("7500.00"))),
                "6 Weeks",
                LocalDate.of(2026, 12, 31),
                "Updated notes"
        );

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));

        assertThrows(IllegalStateException.class, () -> proposalService.update(testProposal.getId(), command));
    }

    @Test
    void publish_shouldPublishDraftProposal() {
        PublishProposalCommand command = new PublishProposalCommand(LocalDate.of(2026, 12, 31));

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));
        when(proposalRepository.save(any(Proposal.class))).thenReturn(testProposal);

        ProposalResponse result = proposalService.publish(testProposal.getId(), command);

        assertNotNull(result);
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    void publish_shouldThrowWhenNotDraft() {
        testProposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        PublishProposalCommand command = new PublishProposalCommand(LocalDate.of(2026, 12, 31));

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));

        assertThrows(IllegalStateException.class, () -> proposalService.publish(testProposal.getId(), command));
    }

    @Test
    void recordDecision_shouldAcceptProposal() {
        testProposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        ProposalDecisionCommand command = new ProposalDecisionCommand("ACCEPT", "Accepted by client");

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));
        when(authService.getUserById(testUser.getId())).thenReturn(testUser);
        when(proposalRepository.save(any(Proposal.class))).thenReturn(testProposal);

        ProposalResponse result = proposalService.recordDecision(testProposal.getId(), command, testUser.getId().toString());

        assertNotNull(result);
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    void recordDecision_shouldDeclineProposal() {
        testProposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        ProposalDecisionCommand command = new ProposalDecisionCommand("DECLINE", "Declined by client");

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));
        when(authService.getUserById(testUser.getId())).thenReturn(testUser);
        when(proposalRepository.save(any(Proposal.class))).thenReturn(testProposal);

        ProposalResponse result = proposalService.recordDecision(testProposal.getId(), command, testUser.getId().toString());

        assertNotNull(result);
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    void recordDecision_shouldThrowWhenNotAuthorized() {
        testProposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        ProposalDecisionCommand command = new ProposalDecisionCommand("ACCEPT", "Accepted");

        User otherUser = new User(
                UUID.randomUUID(),
                "other@example.com",
                "other@example.com",
                "encodedPassword",
                com.growthprint.backend.auth.entity.UserRole.CLIENT,
                com.growthprint.backend.auth.entity.AccountStatus.ACTIVE,
                UUID.randomUUID(), // Different client ID
                Instant.now(),
                Instant.now()
        );

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));
        when(authService.getUserById(otherUser.getId())).thenReturn(otherUser);

        assertThrows(IllegalStateException.class, () -> proposalService.recordDecision(testProposal.getId(), command, otherUser.getId().toString()));
    }

    @Test
    void recordDecision_shouldThrowWhenNotAwaitingDecision() {
        testProposal.setStatus(ProposalStatus.DRAFT);
        ProposalDecisionCommand command = new ProposalDecisionCommand("ACCEPT", "Accepted");

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));
        when(authService.getUserById(testUser.getId())).thenReturn(testUser);

        assertThrows(IllegalStateException.class, () -> proposalService.recordDecision(testProposal.getId(), command, testUser.getId().toString()));
    }

    @Test
    void recordDecision_shouldThrowWhenExpired() {
        testProposal.setStatus(ProposalStatus.SENT_FOR_REVIEW);
        testProposal.setValidUntil(LocalDate.now().minusDays(1)); // Expired
        ProposalDecisionCommand command = new ProposalDecisionCommand("ACCEPT", "Accepted");

        when(proposalRepository.findById(testProposal.getId())).thenReturn(Optional.of(testProposal));
        when(authService.getUserById(testUser.getId())).thenReturn(testUser);
        when(proposalRepository.save(any(Proposal.class))).thenReturn(testProposal);

        assertThrows(IllegalStateException.class, () -> proposalService.recordDecision(testProposal.getId(), command, testUser.getId().toString()));
    }
}
