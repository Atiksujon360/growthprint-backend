package com.growthprint.backend.support;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.project.repository.ProjectRepository;
import com.growthprint.backend.support.controller.SupportRequestController;
import com.growthprint.backend.support.entity.BusinessImpact;
import com.growthprint.backend.support.entity.SupportCategory;
import com.growthprint.backend.support.entity.SupportEstimate;
import com.growthprint.backend.support.entity.SupportEstimateStatus;
import com.growthprint.backend.support.entity.SupportRequest;
import com.growthprint.backend.support.entity.SupportRequestStatus;
import com.growthprint.backend.support.entity.Urgency;
import com.growthprint.backend.support.repository.SupportEstimateRepository;
import com.growthprint.backend.support.repository.SupportRequestRepository;
import com.growthprint.backend.support.service.SupportRequestService;
import com.growthprint.backend.testsupport.StandaloneMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for confirmed support-request defects (audit 2026-11-01: H1, H2, H3).
 */
class SupportRequestDefectRegressionTest {

    private final SupportRequestRepository requestRepository = mock(SupportRequestRepository.class);
    private final SupportEstimateRepository estimateRepository = mock(SupportEstimateRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final AuthService authService = mock(AuthService.class);

    private SupportRequestService service;
    private MockMvc mvc;

    private final UUID clientId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private User clientUser;

    @BeforeEach
    void setUp() {
        service = new SupportRequestService(requestRepository, estimateRepository, clientRepository,
                projectRepository, authService);
        mvc = StandaloneMvc.of(new SupportRequestController(service));

        Client client = new Client(clientId, "Acme", "Acme LLC", "ops@acme.test", null, "https://acme.test",
                ClientEnums.ClientStatus.ACTIVE, null, null, null, LocalDate.now(), "AM", ClientEnums.BillingCurrency.USD,
                null);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        clientUser = new User(userId, "Real.Client@acme.test", "real.client@acme.test", "hash", UserRole.CLIENT,
                AccountStatus.ACTIVE, clientId, Instant.now(), Instant.now());
        when(authService.getUserById(userId)).thenReturn(clientUser);

        // Simulate JPA: assign id/timestamps on save and return the same instance.
        when(requestRepository.save(any(SupportRequest.class))).thenAnswer(invocation -> {
            SupportRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(UUID.randomUUID());
            }
            request.setCreatedAt(Instant.now());
            request.setUpdatedAt(Instant.now());
            return request;
        });
        when(estimateRepository.save(any(SupportEstimate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.findByReference(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        StandaloneMvc.clearAuthentication();
    }

    // ---- H1: reference numbers --------------------------------------------------------------

    @Test
    void referencesUseCurrentUtcYearAndDoNotCollide() throws Exception {
        StandaloneMvc.authenticateAs(userId, "CLIENT");
        ArgumentCaptor<SupportRequest> saved = ArgumentCaptor.forClass(SupportRequest.class);

        int count = 1_500;
        for (int i = 0; i < count; i++) {
            service.createMyRequest(new com.growthprint.backend.support.dto.CreateMySupportRequestCommand(
                    null, "Title " + i, "INCLUDED_SUPPORT", "Description", "LOW", "NORMAL"), userId.toString());
        }
        verify(requestRepository, org.mockito.Mockito.times(count)).save(saved.capture());

        Set<String> references = new HashSet<>();
        String expectedPrefix = "GP-REQ-" + Year.now(ZoneOffset.UTC).getValue() + "-";
        for (SupportRequest request : saved.getAllValues()) {
            assertThat(request.getReference()).startsWith(expectedPrefix).hasSizeLessThanOrEqualTo(50);
            references.add(request.getReference());
        }
        // The old scheme (random 100..999) can produce at most 900 distinct values.
        assertThat(references).hasSize(count);
    }

    // ---- H2: re-classification must never leave two estimates on one request ------------------
    // Decision 2026-11-01 (user): keep the V16 approach — re-classifying is allowed in any status and
    // removes the existing estimate (support_estimates.request_id is UNIQUE since V16). This replaces the
    // earlier "reject re-classification" guard; this test pins the kept behaviour.

    @Test
    void reclassifyingRemovesTheExistingEstimateSoAtMostOneCanExist() throws Exception {
        SupportRequest request = existingRequest(SupportRequestStatus.AWAITING_CLIENT_APPROVAL);
        SupportEstimate existing = pendingEstimate(request.getId());
        when(estimateRepository.findByRequestId(request.getId())).thenReturn(Optional.of(existing));

        mvc.perform(post("/api/v1/support-requests/{id}/classify", request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"PAID_AD_HOC_SUPPORT\",\"adminNotes\":\"again\"}"))
                .andExpect(status().isOk());

        verify(estimateRepository).delete(existing);
        assertThat(request.getStatus()).isEqualTo(SupportRequestStatus.ESTIMATE_PENDING);
    }

    // ---- H3: missing @Valid, enum parsing, spoofable approvedBy -------------------------------

    @Test
    void attachEstimateWithEmptyBodyIsA400WithFieldErrorsNotA500() throws Exception {
        SupportRequest request = existingRequest(SupportRequestStatus.ESTIMATE_PENDING);

        mvc.perform(post("/api/v1/support-requests/{id}/estimate", request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.fieldErrors.hourlyRate").exists());

        verify(estimateRepository, never()).save(any());
    }

    @Test
    void createMyRequestWithOverlongTitleIsRejected() throws Exception {
        StandaloneMvc.authenticateAs(userId, "CLIENT");
        String title = "x".repeat(201); // support_requests.title is VARCHAR(200)

        mvc.perform(post("/api/v1/support-requests/my")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"category\":\"INCLUDED_SUPPORT\","
                                + "\"description\":\"d\",\"businessImpact\":\"LOW\",\"urgency\":\"NORMAL\"}"))
                .andExpect(status().isBadRequest());

        verify(requestRepository, never()).save(any());
    }

    @Test
    void invalidCategoryGivesCleanMessageWithoutJavaClassNames() throws Exception {
        StandaloneMvc.authenticateAs(userId, "CLIENT");

        MvcResult result = mvc.perform(post("/api/v1/support-requests/my")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Broken checkout\",\"category\":\"NOT_A_CATEGORY\","
                                + "\"description\":\"d\",\"businessImpact\":\"LOW\",\"urgency\":\"NORMAL\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("INCLUDED_SUPPORT").doesNotContain("com.growthprint").doesNotContain("enum constant");
    }

    @Test
    void clientEstimateApprovalRecordsTheAuthenticatedUserNotTheSubmittedName() throws Exception {
        StandaloneMvc.authenticateAs(userId, "CLIENT");
        SupportRequest request = existingRequest(SupportRequestStatus.AWAITING_CLIENT_APPROVAL);
        SupportEstimate estimate = pendingEstimate(request.getId());
        when(estimateRepository.findByRequestId(request.getId())).thenReturn(Optional.of(estimate));
        when(estimateRepository.findFirstByRequestIdOrderByCreatedAtDesc(request.getId()))
                .thenReturn(Optional.of(estimate));

        mvc.perform(post("/api/v1/support-requests/my/{id}/approve-estimate", request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\":\"The CEO (spoofed)\"}"))
                .andExpect(status().isOk());

        assertThat(estimate.getAcceptedBy()).isEqualTo("Real.Client@acme.test");
        assertThat(estimate.getStatus()).isEqualTo(SupportEstimateStatus.ACCEPTED);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private SupportRequest existingRequest(SupportRequestStatus status) {
        SupportRequest request = new SupportRequest();
        request.setId(UUID.randomUUID());
        request.setReference("GP-REQ-2026-ABCDEF01");
        request.setClientId(clientId);
        request.setTitle("Existing");
        request.setCategory(SupportCategory.PAID_AD_HOC_SUPPORT);
        request.setDescription("Existing request");
        request.setBusinessImpact(BusinessImpact.MEDIUM);
        request.setUrgency(Urgency.NORMAL);
        request.setStatus(status);
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(requestRepository.findAll()).thenReturn(List.of(request));
        return request;
    }

    private SupportEstimate pendingEstimate(UUID requestId) {
        SupportEstimate estimate = new SupportEstimate();
        estimate.setId(UUID.randomUUID());
        estimate.setRequestId(requestId);
        estimate.setEstimatedHours(new BigDecimal("2.00"));
        estimate.setHourlyRate(new BigDecimal("100.00"));
        estimate.setSubtotal(new BigDecimal("200.00"));
        estimate.setDiscount(BigDecimal.ZERO);
        estimate.setTotalAmount(new BigDecimal("200.00"));
        estimate.setCurrency("USD");
        estimate.setDeliveryTimelineDays(3);
        estimate.setExplanation("Two hours");
        estimate.setStatus(SupportEstimateStatus.PENDING_APPROVAL);
        estimate.setCreatedAt(Instant.now());
        return estimate;
    }
}
