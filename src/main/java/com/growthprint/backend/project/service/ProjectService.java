package com.growthprint.backend.project.service;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.project.dto.CreateMilestoneCommand;
import com.growthprint.backend.project.dto.CreateProjectCommand;
import com.growthprint.backend.project.dto.MilestoneResponse;
import com.growthprint.backend.project.dto.ProjectResponse;
import com.growthprint.backend.project.dto.UpdateProjectStatusCommand;
import com.growthprint.backend.project.entity.Milestone;
import com.growthprint.backend.project.entity.Project;
import com.growthprint.backend.project.exception.ProjectAlreadyExistsException;
import com.growthprint.backend.project.exception.ProjectNotFoundException;
import com.growthprint.backend.project.repository.MilestoneRepository;
import com.growthprint.backend.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final ClientRepository clientRepository;
    private final com.growthprint.backend.auth.repository.UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectRepository projectRepository, MilestoneRepository milestoneRepository,
                          ClientRepository clientRepository, com.growthprint.backend.auth.repository.UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        List<Project> projects = projectRepository.findAll();

        // Batch fetch all clients to avoid N+1 queries
        List<UUID> clientIds = projects.stream()
                        .map(Project::getClientId)
                        .distinct()
                        .toList();

        Map<UUID, Client> clientMap = new HashMap<>();
        if (!clientIds.isEmpty()) {
            clientRepository.findAllById(clientIds).forEach(client ->
                    clientMap.put(client.getId(), client));
        }

        return projects.stream()
                        .map(project -> toResponseWithMilestones(project, clientMap))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));

        Client projectClient = clientRepository.findById(project.getClientId())
                        .orElse(null);
        Map<UUID, Client> clientMap = new HashMap<>();
        if (projectClient != null) {
            clientMap.put(project.getClientId(), projectClient);
        }

        return toResponseWithMilestones(project, clientMap);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findByClientId(UUID userId) {
        // Get user's client ID
        com.growthprint.backend.auth.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new ProjectNotFoundException("User not found"));

        if (user.getClientId() == null) {
            return List.of();
        }

        List<Project> projects = projectRepository.findByClientId(user.getClientId());

        // Batch fetch all clients to avoid N+1 queries
        List<UUID> clientIds = projects.stream()
                        .map(Project::getClientId)
                        .distinct()
                        .toList();

        Map<UUID, Client> clientMap = new HashMap<>();
        if (!clientIds.isEmpty()) {
            clientRepository.findAllById(clientIds).forEach(client ->
                    clientMap.put(client.getId(), client));
        }

        return projects.stream()
                        .map(project -> toResponseWithMilestones(project, clientMap))
                .collect(Collectors.toList());
    }

    @Transactional
    public ProjectResponse create(CreateProjectCommand command) {
        if (projectRepository.existsByReference(command.reference())) {
            throw new ProjectAlreadyExistsException("Project with this reference already exists");
        }

        Client projectClient = clientRepository.findById(UUID.fromString(command.clientId()))
                .orElseThrow(() -> new ProjectNotFoundException("Client not found"));

        Project project = new Project(
                UUID.randomUUID(),
                command.reference(),
                command.name(),
                projectClient.getId(),
                command.serviceCategory(),
                command.description(),
                command.status(),
                command.progressPercent(),
                command.startDate(),
                command.targetCompletionDate(),
                command.budgetTotal(),
                command.budgetSpent(),
                command.assignedLead(),
                        serializeDeliverables(command.deliverables()),
                                Instant.now(),
                Instant.now()
        );

        Project savedProject = projectRepository.save(project);

        Client savedClient = clientRepository.findById(savedProject.getClientId())
                        .orElse(null);
        Map<UUID, Client> clientMap = new HashMap<>();
        if (savedClient != null) {
            clientMap.put(savedProject.getClientId(), savedClient);
        }

        return toResponseWithMilestones(savedProject, clientMap);
    }

    @Transactional
    public ProjectResponse updateStatus(UUID id, UpdateProjectStatusCommand command) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));

        project.setStatus(command.status());
        project.preUpdate();

        Project updatedProject = projectRepository.save(project);

        Client projectClient = clientRepository.findById(updatedProject.getClientId())
                        .orElse(null);
        Map<UUID, Client> clientMap = new HashMap<>();
        if (projectClient != null) {
            clientMap.put(updatedProject.getClientId(), projectClient);
        }

        return toResponseWithMilestones(updatedProject, clientMap);
    }

    @Transactional
    public MilestoneResponse createMilestone(UUID projectId, CreateMilestoneCommand command) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));

        Milestone milestone = new Milestone(
                UUID.randomUUID(),
                project.getId(),
                command.title(),
                command.dueDate(),
                command.status(),
                Instant.now(),
                Instant.now()
        );

        Milestone savedMilestone = milestoneRepository.save(milestone);
        return toMilestoneResponse(savedMilestone);
    }

    private ProjectResponse toResponseWithMilestone(Project project, List<Milestone> milestones,
                    Map<UUID, Client> clientMap) {
            Client client = clientMap.get(project.getClientId());

        return new ProjectResponse(
                project.getId().toString(),
                project.getReference(),
                project.getName(),
                project.getClientId().toString(),
                client != null ? client.getName() : "Unknown Client",
                project.getServiceCategory().name(),
                project.getDescription(),
                project.getStatus().name(),
                project.getProgressPercent(),
                project.getStartDate().toString(),
                project.getTargetCompletionDate().toString(),
                project.getBudgetTotal(),
                project.getBudgetSpent(),
                milestones.stream()
                        .map(this::toMilestoneResponse)
                        .collect(Collectors.toList()),
                deserializeDeliverables(project.getDeliverables()),
                project.getAssignedLead(),
                project.getUpdatedAt().toString()
        );
    }

    private ProjectResponse toResponseWithMilestones(Project project, Map<UUID, Client> clientMap) {
        List<Milestone> milestones = milestoneRepository.findByProjectId(project.getId());
        return toResponseWithMilestone(project, milestones, clientMap);
    }

    private MilestoneResponse toMilestoneResponse(Milestone milestone) {
        return new MilestoneResponse(
                milestone.getId().toString(),
                milestone.getTitle(),
                milestone.getDueDate().toString(),
                milestone.getStatus().name()
        );
    }

    private String serializeDeliverables(List<String> deliverables) {
        try {
            return objectMapper.writeValueAsString(deliverables);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> deserializeDeliverables(String deliverablesJson) {
        try {
            if (deliverablesJson == null || deliverablesJson.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(deliverablesJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
