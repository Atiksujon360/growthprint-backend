package com.growthprint.backend.support.service;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.project.entity.Project;
import com.growthprint.backend.project.repository.ProjectRepository;
import com.growthprint.backend.support.dto.*;
import com.growthprint.backend.support.entity.*;
import com.growthprint.backend.support.exception.InvalidSupportRequestStatusTransitionException;
import com.growthprint.backend.support.exception.SupportRequestNotFoundException;
import com.growthprint.backend.support.repository.SupportEstimateRepository;
import com.growthprint.backend.support.repository.SupportRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportRequestServiceTest {

    @Mock
    private SupportRequestRepository supportRequestRepository;

    @Mock
    private SupportEstimateRepository supportEstimateRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private SupportRequestService supportRequestService;

    private SupportRequest testRequest;
    private Client testClient;
    private Project testProject;

    @BeforeEach
    void setUp() {
        testClient = mock(Client.class);
        when(testClient.getId()).thenReturn(UUID.randomUUID());
        when(testClient.getName()).thenReturn("Test Client");

        testProject = mock(Project.class);
        when(testProject.getId()).thenReturn(UUID.randomUUID());
        when(testProject.getName()).thenReturn("Test Project");

        testRequest = new SupportRequest();
        testRequest.setId(UUID.randomUUID());
        testRequest.setReference("GP-REQ-2026-123");
        testRequest.setClientId(testClient.getId());
        testRequest.setProjectId(testProject.getId());
        testRequest.setTitle("Test Request");
        testRequest.setCategory(SupportCategory.PAID_AD_HOC_SUPPORT);
        testRequest.setDescription("Test description");
        testRequest.setBusinessImpact(BusinessImpact.MEDIUM);
        testRequest.setUrgency(Urgency.NORMAL);
        testRequest.setStatus(SupportRequestStatus.NEW);
        testRequest.setCreatedAt(Instant.now());
        testRequest.setUpdatedAt(Instant.now());
    }

    @Test
    void findMyRequests_ReturnsRequestsForClient() {
        UUID userId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        User testUser = mock(User.class);
        when(testUser.getClientId()).thenReturn(clientId);

        Client mockClient = mock(Client.class);
        when(mockClient.getId()).thenReturn(clientId);
        when(mockClient.getName()).thenReturn("Test Client");

        when(authService.getUserById(userId)).thenReturn(testUser);
        when(supportRequestRepository.findByClientId(clientId)).thenReturn(List.of(testRequest));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(mockClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));

        var result = supportRequestService.findMyRequests(userId.toString());

        assertEquals(1, result.size());
        verify(authService).getUserById(userId);
        verify(supportRequestRepository).findByClientId(clientId);
    }

    @Test
    void findMyRequests_ThrowsWhenUserNotAssociatedWithClient() {
        UUID userId = UUID.randomUUID();
        User testUser = mock(User.class);
        when(testUser.getClientId()).thenReturn(null);

        when(authService.getUserById(userId)).thenReturn(testUser);

        assertThrows(IllegalArgumentException.class, () -> supportRequestService.findMyRequests(userId.toString()));
        verify(authService).getUserById(userId);
        verify(supportRequestRepository, never()).findByClientId(any());
    }

    @Test
    void findMyRequests_ReturnsEmptyListWhenNoRequests() {
        UUID userId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        User testUser = mock(User.class);
        when(testUser.getClientId()).thenReturn(clientId);

        when(authService.getUserById(userId)).thenReturn(testUser);
        when(supportRequestRepository.findByClientId(clientId)).thenReturn(List.of());

        var result = supportRequestService.findMyRequests(userId.toString());

        assertEquals(0, result.size());
        verify(authService).getUserById(userId);
        verify(supportRequestRepository).findByClientId(clientId);
    }

    @Test
    void findAll_ReturnsAllRequests() {
        when(supportRequestRepository.findAll()).thenReturn(List.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));

        var result = supportRequestService.findAll();

        assertEquals(1, result.size());
        verify(supportRequestRepository).findAll();
    }

    @Test
    void findById_ReturnsRequest() {
        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));

        var result = supportRequestService.findById(testRequest.getId());

        assertNotNull(result);
        assertEquals(testRequest.getId().toString(), result.id());
        verify(supportRequestRepository).findById(testRequest.getId());
    }

    @Test
    void findById_ThrowsWhenNotFound() {
        when(supportRequestRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(SupportRequestNotFoundException.class, () -> supportRequestService.findById(UUID.randomUUID()));
    }

    @Test
    void create_CreatesRequest() {
        CreateSupportRequestCommand command = new CreateSupportRequestCommand(
                testClient.getId().toString(),
                null,
                "New Request",
                "PAID_AD_HOC_SUPPORT",
                "Description",
                "MEDIUM",
                "NORMAL"
        );

        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        supportRequestService.create(command);

        verify(supportRequestRepository).save(any());
    }

    @Test
    void classify_UpdatesStatusToReadyToStart() {
        ClassifyRequestCommand command = new ClassifyRequestCommand("INCLUDED_SUPPORT", "Admin notes");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        supportRequestService.classify(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.READY_TO_START, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
    }

    @Test
    void classify_UpdatesStatusToEstimatePending() {
        ClassifyRequestCommand command = new ClassifyRequestCommand("PAID_AD_HOC_SUPPORT", "Admin notes");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        supportRequestService.classify(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.ESTIMATE_PENDING, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
    }

    @Test
    void attachEstimate_UpdatesStatusToAwaitingApproval() {
        testRequest.setStatus(SupportRequestStatus.ESTIMATE_PENDING);
        AttachEstimateCommand command = new AttachEstimateCommand(
                new BigDecimal("5.0"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                7,
                "Test explanation"
        );

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);
        when(supportEstimateRepository.save(any())).thenReturn(new SupportEstimate());

        supportRequestService.attachEstimate(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.AWAITING_CLIENT_APPROVAL, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
        verify(supportEstimateRepository).save(any(SupportEstimate.class));
    }

    @Test
    void attachEstimate_ThrowsWhenNotEstimatePending() {
        testRequest.setStatus(SupportRequestStatus.NEW);
        AttachEstimateCommand command = new AttachEstimateCommand(
                new BigDecimal("5.0"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                7,
                "Test explanation"
        );

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));

        assertThrows(InvalidSupportRequestStatusTransitionException.class,
                () -> supportRequestService.attachEstimate(testRequest.getId(), command));
    }

    @Test
    void attachEstimate_ThrowsWhenDiscountExceedsSubtotal() {
        testRequest.setStatus(SupportRequestStatus.ESTIMATE_PENDING);
        AttachEstimateCommand command = new AttachEstimateCommand(
                new BigDecimal("5.0"),
                new BigDecimal("100.00"),
                new BigDecimal("600.00"), // discount exceeds subtotal (5 * 100 = 500)
                7,
                "Test explanation"
        );

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));

        assertThrows(IllegalArgumentException.class,
                () -> supportRequestService.attachEstimate(testRequest.getId(), command));
    }

    @Test
    void approveEstimate_UpdatesStatusToReadyToStart() {
        testRequest.setStatus(SupportRequestStatus.AWAITING_CLIENT_APPROVAL);
        ApproveEstimateCommand command = new ApproveEstimateCommand("admin@example.com");

        SupportEstimate testEstimate = new SupportEstimate();
        testEstimate.setId(UUID.randomUUID());
        testEstimate.setRequestId(testRequest.getId());
        testEstimate.setEstimatedHours(new BigDecimal("5.0"));
        testEstimate.setHourlyRate(new BigDecimal("100.00"));
        testEstimate.setSubtotal(new BigDecimal("500.00"));
        testEstimate.setDiscount(BigDecimal.ZERO);
        testEstimate.setTotalAmount(new BigDecimal("500.00"));
        testEstimate.setCurrency("USD");
        testEstimate.setDeliveryTimelineDays(7);
        testEstimate.setExplanation("Test explanation");
        testEstimate.setStatus(SupportEstimateStatus.PENDING_APPROVAL);
        testEstimate.setCreatedAt(Instant.now());

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(supportEstimateRepository.findByRequestId(testRequest.getId())).thenReturn(Optional.of(testEstimate));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);
        when(supportEstimateRepository.save(any())).thenReturn(testEstimate);

        supportRequestService.approveEstimate(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.READY_TO_START, testRequest.getStatus());
        assertEquals(SupportEstimateStatus.ACCEPTED, testEstimate.getStatus());
        assertEquals("admin@example.com", testEstimate.getAcceptedBy());
        assertNotNull(testEstimate.getAcceptedAt());
        verify(supportRequestRepository).save(testRequest);
        verify(supportEstimateRepository).save(testEstimate);
    }

    @Test
    void approveEstimate_ThrowsWhenNotAwaitingApproval() {
        testRequest.setStatus(SupportRequestStatus.NEW);
        ApproveEstimateCommand command = new ApproveEstimateCommand("admin@example.com");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));

        assertThrows(InvalidSupportRequestStatusTransitionException.class,
                () -> supportRequestService.approveEstimate(testRequest.getId(), command));
    }

    @Test
    void updateStatus_UpdatesStatus() {
        testRequest.setStatus(SupportRequestStatus.READY_TO_START);
        UpdateRequestStatusCommand command = new UpdateRequestStatusCommand("IN_PROGRESS");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        supportRequestService.updateStatus(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.IN_PROGRESS, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
    }

    @Test
    void updateStatus_ThrowsForInvalidTransition() {
        testRequest.setStatus(SupportRequestStatus.COMPLETED);
        UpdateRequestStatusCommand command = new UpdateRequestStatusCommand("NEW");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));

        assertThrows(InvalidSupportRequestStatusTransitionException.class,
                () -> supportRequestService.updateStatus(testRequest.getId(), command));
    }

    @Test
    void updateStatus_ThrowsForDirectNewToEstimatePending() {
        testRequest.setStatus(SupportRequestStatus.NEW);
        UpdateRequestStatusCommand command = new UpdateRequestStatusCommand("ESTIMATE_PENDING");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));

        assertThrows(InvalidSupportRequestStatusTransitionException.class,
                () -> supportRequestService.updateStatus(testRequest.getId(), command));
    }

    @Test
    void updateStatus_AllowsIdempotentSameStatus() {
        testRequest.setStatus(SupportRequestStatus.IN_PROGRESS);
        UpdateRequestStatusCommand command = new UpdateRequestStatusCommand("IN_PROGRESS");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        supportRequestService.updateStatus(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.IN_PROGRESS, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
    }
}
