package com.growthprint.backend.support.service;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.common.validation.EnumValues;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SupportRequestService {

    /** Classification is only meaningful before an estimate exists (prevents duplicate estimates). */
    private static final Set<SupportRequestStatus> CLASSIFIABLE_STATUSES =
            EnumSet.of(SupportRequestStatus.NEW, SupportRequestStatus.NEEDS_CLASSIFICATION);
    private static final int MAX_REFERENCE_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SupportRequestRepository supportRequestRepository;
    private final SupportEstimateRepository supportEstimateRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final AuthService authService;

    public SupportRequestService(
            SupportRequestRepository supportRequestRepository,
            SupportEstimateRepository supportEstimateRepository,
            ClientRepository clientRepository,
            ProjectRepository projectRepository,
            AuthService authService) {
        this.supportRequestRepository = supportRequestRepository;
        this.supportEstimateRepository = supportEstimateRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<SupportRequestResponse> findAll() {
        return toResponses(supportRequestRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<SupportRequestResponse> findByClientId(UUID clientId) {
        return toResponses(supportRequestRepository.findByClientId(clientId));
    }

    @Transactional(readOnly = true)
    public List<SupportRequestResponse> findMyRequests(String userId) {
        User user = authService.getUserById(UUID.fromString(userId));
        if (user.getClientId() == null) {
            throw new IllegalArgumentException("User is not associated with a client");
        }
        return toResponses(supportRequestRepository.findByClientId(user.getClientId()));
    }

    @Transactional(readOnly = true)
    public SupportRequestResponse findById(UUID id) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));
        return toResponse(request);
    }

    @Transactional
    public SupportRequestResponse create(CreateSupportRequestCommand command) {
        Client client = clientRepository.findById(parseUuid(command.clientId(), "clientId"))
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        Project project = null;
        if (command.projectId() != null && !command.projectId().isBlank()) {
            project = projectRepository.findById(parseUuid(command.projectId(), "projectId"))
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
            if (!project.getClientId().equals(client.getId())) {
                throw new IllegalArgumentException("Project does not belong to the selected client");
            }
        }

        SupportRequest request = newRequest(client.getId(), project, command.title(), command.category(),
                command.description(), command.businessImpact(), command.urgency());
        return toResponse(supportRequestRepository.save(request));
    }

    @Transactional
    public SupportRequestResponse createMyRequest(CreateMySupportRequestCommand command, String userId) {
        User user = authService.getUserById(UUID.fromString(userId));
        if (user.getClientId() == null) {
            throw new IllegalArgumentException("User is not associated with a client");
        }

        Client client = clientRepository.findById(user.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        Project project = null;
        if (command.projectId() != null && !command.projectId().isBlank()) {
            project = projectRepository.findById(parseUuid(command.projectId(), "projectId"))
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
            // Verify project belongs to the client
            if (!project.getClientId().equals(user.getClientId())) {
                throw new IllegalArgumentException("Project does not belong to the authenticated client");
            }
        }

        SupportRequest request = newRequest(client.getId(), project, command.title(), command.category(),
                command.description(), command.businessImpact(), command.urgency());
        return toResponse(supportRequestRepository.save(request));
    }

    @Transactional
    public SupportRequestResponse classify(UUID id, ClassifyRequestCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        // Re-classifying after an estimate exists used to reset the request to ESTIMATE_PENDING and allow a
        // second estimate; support_estimates has no unique(request_id), which then broke every read.
        if (!CLASSIFIABLE_STATUSES.contains(request.getStatus())) {
            throw new InvalidSupportRequestStatusTransitionException(
                    "Requests can only be classified while NEW or NEEDS_CLASSIFICATION (current: "
                            + request.getStatus() + ")");
        }

        request.setCategory(EnumValues.parse(SupportCategory.class, command.category(), "category"));
        request.setAdminNotes(command.adminNotes());

        if (request.getCategory() == SupportCategory.INCLUDED_SUPPORT) {
            request.setStatus(SupportRequestStatus.READY_TO_START);
        } else {
            request.setStatus(SupportRequestStatus.ESTIMATE_PENDING);
        }

        return toResponse(supportRequestRepository.save(request));
    }

    @Transactional
    public SupportRequestResponse attachEstimate(UUID id, AttachEstimateCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        if (request.getStatus() != SupportRequestStatus.ESTIMATE_PENDING) {
            throw new InvalidSupportRequestStatusTransitionException(
                    "Estimates can only be attached to requests in ESTIMATE_PENDING status");
        }
        if (supportEstimateRepository.findFirstByRequestIdOrderByCreatedAtDesc(request.getId()).isPresent()) {
            throw new InvalidSupportRequestStatusTransitionException(
                    "An estimate is already attached to this request");
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
        // NOTE: currency is fixed to USD (pre-existing behaviour); per-client currency is an open business
        // question documented in the 2026-11-01 audit.
        estimate.setCurrency("USD");
        estimate.setDeliveryTimelineDays(command.deliveryTimelineDays());
        estimate.setExplanation(command.explanation());
        estimate.setStatus(SupportEstimateStatus.PENDING_APPROVAL);

        supportEstimateRepository.save(estimate);

        request.setStatus(SupportRequestStatus.AWAITING_CLIENT_APPROVAL);

        return toResponse(supportRequestRepository.save(request));
    }

    /**
     * Admin records an approval on the client's behalf (e.g. given by phone/email), so the submitted
     * {@code approvedBy} name is kept for this path.
     */
    @Transactional
    public SupportRequestResponse approveEstimate(UUID id, ApproveEstimateCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));
        return acceptEstimate(request, command.approvedBy());
    }

    @Transactional
    public SupportRequestResponse approveMyEstimate(UUID id, ApproveEstimateCommand command, String userId) {
        // Resolve authenticated user and verify client association
        User user = authService.getUserById(UUID.fromString(userId));
        if (user.getClientId() == null) {
            throw new IllegalArgumentException("User is not associated with a client");
        }

        // Fetch support request and verify ownership. Another client's request is reported as "not found"
        // so that request ids of other clients cannot be probed.
        SupportRequest request = supportRequestRepository.findById(id)
                .filter(found -> user.getClientId().equals(found.getClientId()))
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        // The audit trail records who actually approved: the authenticated user, never a client-supplied name.
        // (approvedBy is still accepted in the request body for backwards compatibility, but ignored here.)
        return acceptEstimate(request, user.getEmail());
    }

    @Transactional
    public SupportRequestResponse updateStatus(UUID id, UpdateRequestStatusCommand command) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new SupportRequestNotFoundException("Support request not found"));

        SupportRequestStatus newStatus = EnumValues.parse(SupportRequestStatus.class, command.status(), "status");

        if (!isValidStatusTransition(request.getStatus(), newStatus)) {
            throw new InvalidSupportRequestStatusTransitionException(
                    String.format("Cannot transition from %s to %s", request.getStatus(), newStatus));
        }

        request.setStatus(newStatus);

        return toResponse(supportRequestRepository.save(request));
    }

    private SupportRequestResponse acceptEstimate(SupportRequest request, String acceptedBy) {
        if (request.getStatus() != SupportRequestStatus.AWAITING_CLIENT_APPROVAL) {
            throw new InvalidSupportRequestStatusTransitionException(
                    "Estimates can only be approved for requests in AWAITING_CLIENT_APPROVAL status");
        }

        SupportEstimate estimate = supportEstimateRepository.findFirstByRequestIdOrderByCreatedAtDesc(request.getId())
                .orElseThrow(() -> new InvalidSupportRequestStatusTransitionException(
                        "No estimate is attached to this request"));
        estimate.setStatus(SupportEstimateStatus.ACCEPTED);
        estimate.setAcceptedAt(Instant.now());
        estimate.setAcceptedBy(acceptedBy);
        supportEstimateRepository.save(estimate);

        request.setStatus(SupportRequestStatus.READY_TO_START);

        return toResponse(supportRequestRepository.save(request));
    }

    private SupportRequest newRequest(UUID clientId, Project project, String title, String category,
                                      String description, String businessImpact, String urgency) {
        SupportRequest request = new SupportRequest();
        request.setReference(generateReference());
        request.setClientId(clientId);
        request.setProjectId(project != null ? project.getId() : null);
        request.setTitle(title);
        request.setCategory(EnumValues.parse(SupportCategory.class, category, "category"));
        request.setDescription(description);
        request.setBusinessImpact(EnumValues.parse(BusinessImpact.class, businessImpact, "businessImpact"));
        request.setUrgency(EnumValues.parse(Urgency.class, urgency, "urgency"));
        request.setStatus(SupportRequestStatus.NEW);
        return request;
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

    /**
     * GP-REQ-{UTC year}-{8 hex chars}: ~4.3 billion values per year (was 900 values with a hard-coded
     * year). Uniqueness is still enforced by the database; the bounded retry only avoids a needless 409.
     */
    private String generateReference() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            String candidate = "GP-REQ-" + year + "-"
                    + String.format(Locale.ROOT, "%08X", RANDOM.nextInt());
            if (supportRequestRepository.findByReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique support request reference");
    }

    private static UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Mapping (lists are batch-loaded: 3 queries in total instead of 3 per request)
    // ---------------------------------------------------------------------------------------------

    private List<SupportRequestResponse> toResponses(List<SupportRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> clientNames = clientRepository.findAllById(distinct(requests, SupportRequest::getClientId))
                .stream().collect(Collectors.toMap(Client::getId, Client::getName, (a, b) -> a));
        Map<UUID, String> projectNames = projectRepository.findAllById(distinct(requests, SupportRequest::getProjectId))
                .stream().collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
        Map<UUID, SupportEstimate> latestEstimates = new HashMap<>();
        supportEstimateRepository.findByRequestIdIn(distinct(requests, SupportRequest::getId)).forEach(estimate ->
                latestEstimates.merge(estimate.getRequestId(), estimate, (a, b) ->
                        Comparator.comparing(SupportEstimate::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())).compare(a, b) >= 0 ? a : b));

        return requests.stream()
                .map(request -> toResponse(request,
                        clientNames.get(request.getClientId()),
                        request.getProjectId() != null ? projectNames.get(request.getProjectId()) : null,
                        latestEstimates.get(request.getId())))
                .collect(Collectors.toList());
    }

    private SupportRequestResponse toResponse(SupportRequest request) {
        String clientName = request.getClientId() != null
                ? clientRepository.findById(request.getClientId()).map(Client::getName).orElse(null)
                : null;
        String projectName = request.getProjectId() != null
                ? projectRepository.findById(request.getProjectId()).map(Project::getName).orElse(null)
                : null;
        SupportEstimate estimate = request.getId() != null
                ? supportEstimateRepository.findFirstByRequestIdOrderByCreatedAtDesc(request.getId()).orElse(null)
                : null;
        return toResponse(request, clientName, projectName, estimate);
    }

    private SupportRequestResponse toResponse(SupportRequest request, String clientName, String projectName,
                                              SupportEstimate est) {
        SupportRequestResponse.SupportEstimateResponse estimate = est == null ? null
                : new SupportRequestResponse.SupportEstimateResponse(
                        est.getId() != null ? est.getId().toString() : null,
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
                        est.getCreatedAt() != null ? est.getCreatedAt().toString() : null,
                        est.getAcceptedAt() != null ? est.getAcceptedAt().toString() : null,
                        est.getAcceptedBy());

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
                request.getCreatedAt() != null ? request.getCreatedAt().toString() : null,
                request.getUpdatedAt() != null ? request.getUpdatedAt().toString() : null,
                estimate
        );
    }

    private static <T> Collection<UUID> distinct(List<T> items, Function<T, UUID> getter) {
        return items.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
