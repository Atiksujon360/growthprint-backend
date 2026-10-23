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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SupportRequestService {

    private final SupportRequestRepository supportRequestRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;

    public SupportRequestService(
            SupportRequestRepository supportRequestRepository,
            ClientRepository clientRepository,
            ProjectRepository projectRepository) {
        this.supportRequestRepository = supportRequestRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<SupportRequestResponse> findAll() {
        List<SupportRequest> requests = supportRequestRepository.findAll();
        return requests.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SupportRequestResponse findById(UUID id) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));
        return toResponse(request);
    }

    @Transactional
    public SupportRequestResponse create(CreateSupportRequestCommand command) {
        Client client = clientRepository.findById(UUID.fromString(command.clientId()))
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        Project project = null;
        if (command.projectId() != null) {
            project = projectRepository.findById(UUID.fromString(command.projectId()))
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        }

        String reference = generateReference();

        SupportRequest request = new SupportRequest();
        request.setReference(reference);
        request.setClientId(client.getId());
        request.setProjectId(project != null ? project.getId() : null);
        request.setTitle(command.title());
        request.setCategory(SupportCategory.valueOf(command.category()));
        request.setDescription(command.description());
        request.setBusinessImpact(BusinessImpact.valueOf(command.businessImpact()));
        request.setUrgency(Urgency.valueOf(command.urgency()));
        request.setStatus(SupportRequestStatus.NEW);

        SupportRequest savedRequest = supportRequestRepository.save(request);
        return toResponse(savedRequest);
    }

    @Transactional
    public SupportRequestResponse classify(UUID id, ClassifyRequestCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        request.setCategory(SupportCategory.valueOf(command.category()));
        request.setAdminNotes(command.adminNotes());

        if (request.getCategory() == SupportCategory.INCLUDED_SUPPORT) {
            request.setStatus(SupportRequestStatus.READY_TO_START);
        } else {
            request.setStatus(SupportRequestStatus.ESTIMATE_PENDING);
        }

        SupportRequest savedRequest = supportRequestRepository.save(request);
        return toResponse(savedRequest);
    }

    @Transactional
    public SupportRequestResponse attachEstimate(UUID id, AttachEstimateCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        if (request.getStatus() != SupportRequestStatus.ESTIMATE_PENDING) {
            throw new InvalidSupportRequestStatusTransitionException(
                    "Estimates can only be attached to requests in ESTIMATE_PENDING status");
        }

        request.setStatus(SupportRequestStatus.AWAITING_CLIENT_APPROVAL);

        SupportRequest savedRequest = supportRequestRepository.save(request);
        return toResponse(savedRequest);
    }

    @Transactional
    public SupportRequestResponse approveEstimate(UUID id, ApproveEstimateCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        if (request.getStatus() != SupportRequestStatus.AWAITING_CLIENT_APPROVAL) {
            throw new InvalidSupportRequestStatusTransitionException(
                    "Estimates can only be approved for requests in AWAITING_CLIENT_APPROVAL status");
        }

        request.setStatus(SupportRequestStatus.READY_TO_START);

        SupportRequest savedRequest = supportRequestRepository.save(request);
        return toResponse(savedRequest);
    }

    @Transactional
    public SupportRequestResponse updateStatus(UUID id, UpdateRequestStatusCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        SupportRequestStatus newStatus = SupportRequestStatus.valueOf(command.status());

        if (!isValidStatusTransition(request.getStatus(), newStatus)) {
            throw new InvalidSupportRequestStatusTransitionException(
                    String.format("Cannot transition from %s to %s", request.getStatus(), newStatus));
        }

        request.setStatus(newStatus);

        SupportRequest savedRequest = supportRequestRepository.save(request);
        return toResponse(savedRequest);
    }

    private boolean isValidStatusTransition(SupportRequestStatus current, SupportRequestStatus newStatus) {
        return true;
    }

    private String generateReference() {
        return "GP-REQ-2026-" + (int) (Math.random() * 900 + 100);
    }

    private SupportRequestResponse toResponse(SupportRequest request) {
        final String clientName = request.getClientId() != null
                ? clientRepository.findById(request.getClientId())
                        .map(Client::getName)
                        .orElse(null)
                : null;

        final String projectName = request.getProjectId() != null
                ? projectRepository.findById(request.getProjectId())
                        .map(Project::getName)
                        .orElse(null)
                : null;

        return new SupportRequestResponse(
                request.getId().toString(),
                request.getReference(),
                request.getClientId().toString(),
                clientName,
                request.getProjectId() != null ? request.getProjectId().toString() : null,
                projectName,
                request.getTitle(),
                request.getCategory().name(),
                request.getDescription(),
                request.getBusinessImpact().name(),
                request.getUrgency().name(),
                request.getStatus().name(),
                request.getAdminNotes(),
                request.getResolutionSummary(),
                request.getCreatedAt().toString(),
                request.getUpdatedAt().toString(),
                null
        );
    }
}
