package com.growthprint.backend.proposal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.proposal.controller.ProposalController;
import com.growthprint.backend.proposal.dto.CreateProposalCommand;
import com.growthprint.backend.proposal.entity.Proposal;
import com.growthprint.backend.proposal.entity.ProposalCurrency;
import com.growthprint.backend.proposal.entity.ProposalStatus;
import com.growthprint.backend.proposal.repository.ProposalRepository;
import com.growthprint.backend.proposal.service.ProposalService;
import com.growthprint.backend.testsupport.StandaloneMvc;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for confirmed proposal defects (audit 2026-11-01: C1 mapping guard, numbering loop,
 * wrong status codes).
 */
class ProposalDefectRegressionTest {

    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final AuthService authService = mock(AuthService.class);

    private ProposalService service;
    private MockMvc mvc;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProposalService(proposalRepository, clientRepository, authService, new ObjectMapper());
        mvc = StandaloneMvc.of(new ProposalController(service));
        Client client = new Client(clientId, "Acme", "Acme LLC", "ops@acme.test", null, "https://acme.test",
                ClientEnums.ClientStatus.ACTIVE, null, null, null, LocalDate.now(), "AM",
                ClientEnums.BillingCurrency.USD, null);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        StandaloneMvc.clearAuthentication();
    }

    @Test
    void creatingAProposalTerminatesWhenTheNextNumberIsAlreadyTaken() {
        // count() + 1 collides (e.g. a gap left by seed/manual data); the old loop regenerated the same
        // number forever because count() never changes inside the loop.
        String taken = String.format("GP-PROP-%d-%04d", Year.now(ZoneOffset.UTC).getValue(), 6);
        when(proposalRepository.count()).thenReturn(5L);
        when(proposalRepository.existsByProposalNumber(anyString()))
                .thenAnswer(invocation -> taken.equals(invocation.getArgument(0)));

        var response = assertTimeoutPreemptively(Duration.ofSeconds(3), () -> service.create(createCommand()));

        assertThat(response.proposalNumber()).isNotEqualTo(taken).startsWith("GP-PROP-");
    }

    @Test
    void unknownProposalIs404NotA400() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(proposalRepository.findById(unknown)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/proposals/{id}", unknown)).andExpect(status().isNotFound());
    }

    @Test
    void decidingOnAnotherClientsProposalIs403NotA409() throws Exception {
        UUID otherClientsUser = UUID.randomUUID();
        when(authService.getUserById(otherClientsUser)).thenReturn(new User(otherClientsUser, "b@other.test",
                "b@other.test", "hash", UserRole.CLIENT, AccountStatus.ACTIVE, UUID.randomUUID(), Instant.now(),
                Instant.now()));
        Proposal proposal = sentProposal(LocalDate.now(ZoneOffset.UTC).plusDays(10));
        StandaloneMvc.authenticateAs(otherClientsUser, "CLIENT");

        mvc.perform(post("/api/v1/proposals/my/{id}/decision", proposal.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isForbidden());

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SENT_FOR_REVIEW);
    }

    @Test
    void jsonbColumnsAreMappedAsJsonSoPostgresAcceptsThem() throws Exception {
        // Without @JdbcTypeCode(SqlTypes.JSON) Hibernate binds these as VARCHAR and PostgreSQL rejects
        // "column ... is of type jsonb but expression is of type character varying" (proven by the
        // PostgreSQL integration test ProposalWorkflowIntegrationTest when a test DB is available).
        for (String field : List.of("deliverables", "exclusions", "paymentMilestones")) {
            JdbcTypeCode typeCode = Proposal.class.getDeclaredField(field).getAnnotation(JdbcTypeCode.class);
            assertThat(typeCode).as("@JdbcTypeCode on Proposal.%s", field).isNotNull();
            assertThat(typeCode.value()).isEqualTo(SqlTypes.JSON);
        }
    }

    private Proposal sentProposal(LocalDate validUntil) {
        Proposal proposal = new Proposal(UUID.randomUUID(), "GP-PROP-2026-0001", clientId, "Acme", "Rebuild",
                "SOFTWARE_DEV", "Rebuild the platform", "[\"App\"]", "[]", new BigDecimal("1000.00"),
                ProposalCurrency.USD, "[{\"description\":\"All\",\"percentage\":100,\"amount\":1000.00}]",
                "4 weeks", validUntil, ProposalStatus.SENT_FOR_REVIEW, null, Instant.now(), Instant.now());
        when(proposalRepository.findById(proposal.getId())).thenReturn(Optional.of(proposal));
        return proposal;
    }

    private CreateProposalCommand createCommand() {
        return new CreateProposalCommand(clientId.toString(), "Rebuild", "SOFTWARE_DEV",
                "Rebuild the customer platform", List.of("App"), List.of(), new BigDecimal("1000.00"), "USD",
                List.of(new CreateProposalCommand.PaymentMilestone("All", 100, new BigDecimal("1000.00"))),
                "4 weeks", LocalDate.now(ZoneOffset.UTC).plusDays(30), null);
    }
}
