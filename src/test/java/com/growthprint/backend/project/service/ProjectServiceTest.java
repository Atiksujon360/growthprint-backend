package com.growthprint.backend.project.service;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.project.dto.CreateMilestoneCommand;
import com.growthprint.backend.project.dto.CreateProjectCommand;
import com.growthprint.backend.project.dto.UpdateProjectStatusCommand;
import com.growthprint.backend.project.entity.Milestone;
import com.growthprint.backend.project.entity.MilestoneStatus;
import com.growthprint.backend.project.entity.Project;
import com.growthprint.backend.project.entity.ProjectStatus;
import com.growthprint.backend.project.entity.ServiceCategory;
import com.growthprint.backend.project.exception.ProjectAlreadyExistsException;
import com.growthprint.backend.project.exception.ProjectNotFoundException;
import com.growthprint.backend.project.repository.MilestoneRepository;
import com.growthprint.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ProjectService projectService;

    private Client testClient;
    private Project testProject;

    @BeforeEach
    void setUp() {
        testClient = new Client(
                UUID.randomUUID(),
                "Test Client",
                "Test Client Legal",
                "test@example.com",
                null,
                "https://example.com",
                ClientEnums.ClientStatus.ACTIVE,
                        null,
                null,
                null,
                LocalDate.now(),
                "Test Manager",
                com.growthprint.backend.client.entity.ClientEnums.BillingCurrency.USD,
                null
        );

        testProject = new Project(
                UUID.randomUUID(),
                "GP-PRJ-2026-001",
                "Test Project",
                testClient.getId(),
                ServiceCategory.SOFTWARE_DEV,
                "Test Description",
                ProjectStatus.IN_PROGRESS,
                50,
                LocalDate.now(),
                LocalDate.now().plusMonths(3),
                new BigDecimal("10000.00"),
                new BigDecimal("5000.00"),
                "Test Lead",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void findAll_ReturnsAllProjects() {
        when(projectRepository.findAll()).thenReturn(List.of(testProject));
        when(milestoneRepository.findByProjectId(testProject.getId())).thenReturn(List.of());
        when(clientRepository.findById(testProject.getClientId())).thenReturn(Optional.of(testClient));

        var result = projectService.findAll();

        assertEquals(1, result.size());
        assertEquals("GP-PRJ-2026-001", result.get(0).reference());
    }

    @Test
    void findById_ReturnsProject() {
        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(milestoneRepository.findByProjectId(testProject.getId())).thenReturn(List.of());
        when(clientRepository.findById(testProject.getClientId())).thenReturn(Optional.of(testClient));

        var result = projectService.findById(testProject.getId());

        assertEquals("GP-PRJ-2026-001", result.reference());
        assertEquals("Test Project", result.name());
    }

    @Test
    void findById_ThrowsWhenNotFound() {
        when(projectRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.findById(UUID.randomUUID()));
    }

    @Test
    void create_CreatesProject() {
        CreateProjectCommand command = new CreateProjectCommand(
                "GP-PRJ-2026-002",
                "New Project",
                testClient.getId().toString(),
                ServiceCategory.WEBSITE_MAINTENANCE,
                "New Description",
                ProjectStatus.DISCOVERY,
                0,
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                new BigDecimal("15000.00"),
                null,
                "New Lead",
                List.of("Deliverable 1")
        );

        when(projectRepository.existsByReference("GP-PRJ-2026-002")).thenReturn(false);
        when(clientRepository.findById(testClient.getId())).thenReturn(Optional.of(testClient));
        when(projectRepository.save(any())).thenReturn(testProject);
        when(milestoneRepository.findByProjectId(any())).thenReturn(List.of());
        when(clientRepository.findById(any())).thenReturn(Optional.of(testClient));

        var result = projectService.create(command);

        assertNotNull(result);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void create_ThrowsWhenReferenceExists() {
        CreateProjectCommand command = new CreateProjectCommand(
                "GP-PRJ-2026-001",
                "New Project",
                testClient.getId().toString(),
                ServiceCategory.SOFTWARE_DEV,
                "Description",
                ProjectStatus.DISCOVERY,
                0,
                LocalDate.now(),
                LocalDate.now().plusMonths(3),
                new BigDecimal("10000.00"),
                null,
                null,
                null
        );

        when(projectRepository.existsByReference("GP-PRJ-2026-001")).thenReturn(true);

        assertThrows(ProjectAlreadyExistsException.class, () -> projectService.create(command));
    }

    @Test
    void create_ThrowsWhenClientNotFound() {
        CreateProjectCommand command = new CreateProjectCommand(
                "GP-PRJ-2026-002",
                "New Project",
                UUID.randomUUID().toString(),
                ServiceCategory.SOFTWARE_DEV,
                "Description",
                ProjectStatus.DISCOVERY,
                0,
                LocalDate.now(),
                LocalDate.now().plusMonths(3),
                new BigDecimal("10000.00"),
                null,
                null,
                null
        );

        when(projectRepository.existsByReference(any())).thenReturn(false);
        when(clientRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.create(command));
    }

    @Test
    void updateStatus_UpdatesProjectStatus() {
        UpdateProjectStatusCommand command = new UpdateProjectStatusCommand(ProjectStatus.COMPLETED);

        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenReturn(testProject);
        when(milestoneRepository.findByProjectId(testProject.getId())).thenReturn(List.of());
        when(clientRepository.findById(testProject.getClientId())).thenReturn(Optional.of(testClient));

        var result = projectService.updateStatus(testProject.getId(), command);

        assertEquals(ProjectStatus.COMPLETED, testProject.getStatus());
        verify(projectRepository).save(testProject);
    }

    @Test
    void updateStatus_ThrowsWhenNotFound() {
        UpdateProjectStatusCommand command = new UpdateProjectStatusCommand(ProjectStatus.COMPLETED);

        when(projectRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.updateStatus(UUID.randomUUID(), command));
    }

    @Test
    void createMilestone_CreatesMilestone() {
        CreateMilestoneCommand command = new CreateMilestoneCommand(
                "Milestone 1",
                LocalDate.now().plusWeeks(2),
                MilestoneStatus.PENDING
        );

        when(projectRepository.findById(testProject.getId())).thenReturn(Optional.of(testProject));
        when(milestoneRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = projectService.createMilestone(testProject.getId(), command);

        assertNotNull(result);
        assertEquals("Milestone 1", result.title());
        assertEquals("PENDING", result.status());
    }

    @Test
    void createMilestone_ThrowsWhenProjectNotFound() {
        CreateMilestoneCommand command = new CreateMilestoneCommand(
                "Milestone 1",
                LocalDate.now().plusWeeks(2),
                MilestoneStatus.PENDING
        );

        when(projectRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.createMilestone(UUID.randomUUID(), command));
    }
}
