package com.growthprint.backend.project.service;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.project.dto.CreateMilestoneCommand;
import com.growthprint.backend.project.dto.CreateProjectCommand;
import com.growthprint.backend.project.dto.MilestoneResponse;
import com.growthprint.backend.project.dto.ProjectResponse;
import com.growthprint.backend.project.dto.UpdateProjectStatusCommand;
import com.growthprint.backend.project.entity.Milestone;
import com.growthprint.backend.project.entity.MilestoneStatus;
import com.growthprint.backend.project.entity.Project;
import com.growthprint.backend.project.entity.ProjectStatus;
import com.growthprint.backend.project.exception.ProjectAlreadyExistsException;
import com.growthprint.backend.project.exception.ProjectNotFoundException;
import com.growthprint.backend.project.repository.MilestoneRepository;
import com.growthprint.backend.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final ClientRepository clientRepository;

    public ProjectService(ProjectRepository projectRepository, MilestoneRepository milestoneRepository,
                          ClientRepository clientRepository) {
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        List<Project> projects = projectRepository.findAll();
        return projects.stream()
                .map(this::toResponseWithMilestones)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));
        return toResponseWithMilestones(project);
    }

    @Transactional
    public ProjectResponse create(CreateProjectCommand command) {
        if (projectRepository.existsByReference(command.reference())) {
            throw new ProjectAlreadyExistsException("Project with this reference already exists");
        }

        Client client = clientRepository.findById(UUID.fromString(command.clientId()))
                .orElseThrow(() -> new ProjectNotFoundException("Client not found"));

        Project project = new Project(
                UUID.randomUUID(),
                command.reference(),
                command.name(),
                client.getId(),
                command.serviceCategory(),
                command.description(),
                command.status(),
                command.progressPercent(),
                command.startDate(),
                command.targetCompletionDate(),
                command.budgetTotal(),
                command.budgetSpent(),
                command.assignedLead(),
                Instant.now(),
                Instant.now()
        );

        Project savedProject = projectRepository.save(project);
        return toResponseWithMilestones(savedProject);
    }

    @Transactional
    public ProjectResponse updateStatus(UUID id, UpdateProjectStatusCommand command) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));

        project.setStatus(command.status());
        project.preUpdate();

        Project updatedProject = projectRepository.save(project);
        return toResponseWithMilestones(updatedProject);
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

    private ProjectResponse toResponseWithMilestone(Project project, List<Milestone> milestones) {
        Client client = clientRepository.findById(project.getClientId())
                .orElse(null);

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
                List.of(), // deliverables not stored in DB yet
                project.getAssignedLead(),
                project.getUpdatedAt().toString()
        );
    }

    private ProjectResponse toResponseWithMilestones(Project project) {
        List<Milestone> milestones = milestoneRepository.findByProjectId(project.getId());
        return toResponseWithMilestone(project, milestones);
    }

    private MilestoneResponse toMilestoneResponse(Milestone milestone) {
        return new MilestoneResponse(
                milestone.getId().toString(),
                milestone.getTitle(),
                milestone.getDueDate().toString(),
                milestone.getStatus().name()
        );
    }
}
