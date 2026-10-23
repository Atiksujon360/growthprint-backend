package com.growthprint.backend.support.service;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.project.entity.Project;
import com.growthprint.backend.project.repository.ProjectRepository;
import com.growthprint.backend.support.dto.*;
import com.growthprint.backend.support.entity.*;
import com.growthprint.backend.support.exception.InvalidSupportRequestStatusTransitionException;
import com.growthprint.backend.support.exception.SupportRequestNotFoundException;
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
    private ClientRepository clientRepository;

    @Mock
    private ProjectRepository projectRepository;

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

        var result = supportRequestService.create(command);

        assertNotNull(result);
        verify(supportRequestRepository).save(any());
    }

    @Test
    void classify_UpdatesStatusToReadyToStart() {
        ClassifyRequestCommand command = new ClassifyRequestCommand("INCLUDED_SUPPORT", "Admin notes");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        var result = supportRequestService.classify(testRequest.getId(), command);

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

        var result = supportRequestService.classify(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.ESTIMATE_PENDING, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
    }

    @Test
    void attachEstimate_UpdatesStatusToAwaitingApproval() {
        testRequest.setStatus(SupportRequestStatus.ESTIMATE_PENDING);
        AttachEstimateCommand command = new AttachEstimateCommand(
                new BigDecimal("5.0"),
                new BigDecimal("100.00"),
                7,
                "Test explanation"
        );

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        var result = supportRequestService.attachEstimate(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.AWAITING_CLIENT_APPROVAL, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
    }

    @Test
    void attachEstimate_ThrowsWhenNotEstimatePending() {
        testRequest.setStatus(SupportRequestStatus.NEW);
        AttachEstimateCommand command = new AttachEstimateCommand(
                new BigDecimal("5.0"),
                new BigDecimal("100.00"),
                7,
                "Test explanation"
        );

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));

        assertThrows(InvalidSupportRequestStatusTransitionException.class,
                () -> supportRequestService.attachEstimate(testRequest.getId(), command));
    }

    @Test
    void approveEstimate_UpdatesStatusToReadyToStart() {
        testRequest.setStatus(SupportRequestStatus.AWAITING_CLIENT_APPROVAL);
        ApproveEstimateCommand command = new ApproveEstimateCommand("admin@example.com");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        var result = supportRequestService.approveEstimate(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.READY_TO_START, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
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
        UpdateRequestStatusCommand command = new UpdateRequestStatusCommand("IN_PROGRESS");

        when(supportRequestRepository.findById(testRequest.getId())).thenReturn(Optional.of(testRequest));
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(supportRequestRepository.save(any())).thenReturn(testRequest);

        var result = supportRequestService.updateStatus(testRequest.getId(), command);

        assertEquals(SupportRequestStatus.IN_PROGRESS, testRequest.getStatus());
        verify(supportRequestRepository).save(testRequest);
    }
}
