package com.growthprint.backend.support.service;

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
    private final SupportEstimateRepository supportEstimateRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;

    public SupportRequestService(
            SupportRequestRepository supportRequestRepository,
            SupportEstimateRepository supportEstimateRepository,
            ClientRepository clientRepository,
            ProjectRepository projectRepository) {
        this.supportRequestRepository = supportRequestRepository;
        this.supportEstimateRepository = supportEstimateRepository;
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

        // Calculate estimate totals
        BigDecimal subtotal = command.estimatedHours().multiply(command.hourlyRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = command.discount() != null ? command.discount() : BigDecimal.ZERO;

        // Validate discount does not exceed subtotal
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed subtotal");
        }

        BigDecimal totalAmount = subtotal.subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);

        // Create and persist the estimate
        SupportEstimate estimate = new SupportEstimate();
        estimate.setRequestId(request.getId());
        estimate.setEstimatedHours(command.estimatedHours());
        estimate.setHourlyRate(command.hourlyRate());
        estimate.setSubtotal(subtotal);
        estimate.setDiscount(discount);
        estimate.setTotalAmount(totalAmount);
        estimate.setCurrency("USD");
        estimate.setDeliveryTimelineDays(command.deliveryTimelineDays());
        estimate.setExplanation(command.explanation());
        estimate.setStatus(SupportEstimateStatus.PENDING_APPROVAL);

        supportEstimateRepository.save(estimate);

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

        // Update the estimate status
        SupportEstimate estimate = supportEstimateRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Estimate not found for this request"));
        estimate.setStatus(SupportEstimateStatus.ACCEPTED);
        estimate.setAcceptedAt(Instant.now());
        estimate.setAcceptedBy(command.approvedBy());
        supportEstimateRepository.save(estimate);

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
        // Allow same status (idempotent)
        if (current == newStatus) {
            return true;
        }

        // Define valid transitions based on frontend workflow
        switch (current) {
            case NEW:
                // NEW must go through classification first
                return newStatus == SupportRequestStatus.NEEDS_CLASSIFICATION;
            case NEEDS_CLASSIFICATION:
                // After classification, depends on category (handled in classify() method)
                // Direct transitions only via classify() operation
                return false;
            case ESTIMATE_PENDING:
                // Only transition via attachEstimate operation
                return newStatus == SupportRequestStatus.AWAITING_CLIENT_APPROVAL;
            case AWAITING_CLIENT_APPROVAL:
                // Only transition via approveEstimate operation
                return newStatus == SupportRequestStatus.READY_TO_START;
            case READY_TO_START:
                return newStatus == SupportRequestStatus.IN_PROGRESS
                        || newStatus == SupportRequestStatus.COMPLETED;
            case IN_PROGRESS:
                return newStatus == SupportRequestStatus.WAITING_FOR_CLIENT
                        || newStatus == SupportRequestStatus.COMPLETED;
            case WAITING_FOR_CLIENT:
                return newStatus == SupportRequestStatus.IN_PROGRESS
                        || newStatus == SupportRequestStatus.COMPLETED;
            case COMPLETED:
                // No transitions from COMPLETED
                return false;
            default:
                return false;
        }
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

        // Fetch estimate if exists
        final SupportRequestResponse.SupportEstimateResponse estimate = supportEstimateRepository.findByRequestId(request.getId())
                .map(est -> new SupportRequestResponse.SupportEstimateResponse(
                        est.getId().toString(),
                        est.getRequestId().toString(),
                        est.getEstimatedHours().toString(),
                        est.getHourlyRate().toString(),
                        est.getSubtotal().toString(),
                        est.getDiscount().toString(),
                        est.getTotalAmount().toString(),
                        est.getCurrency(),
                        est.getDeliveryTimelineDays(),
                        est.getExplanation(),
                        est.getStatus().name(),
                        est.getCreatedAt().toString(),
                        est.getAcceptedAt() != null ? est.getAcceptedAt().toString() : null,
                        est.getAcceptedBy()
                ))
                .orElse(null);

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
                estimate
        );
    }
}
