package com.growthprint.backend.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.project.dto.CreateMilestoneCommand;
import com.growthprint.backend.project.dto.CreateProjectCommand;
import com.growthprint.backend.project.dto.UpdateProjectStatusCommand;
import com.growthprint.backend.project.entity.MilestoneStatus;
import com.growthprint.backend.project.entity.ProjectStatus;
import com.growthprint.backend.project.entity.ServiceCategory;
import com.growthprint.backend.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_ReturnsProjects() throws Exception {
        when(projectService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_ReturnsProject() throws Exception {
        when(projectService.findById(any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_CreatesProject() throws Exception {
        CreateProjectCommand command = new CreateProjectCommand(
                "GP-PRJ-2026-001",
                "Test Project",
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

        when(projectService.create(any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_UpdatesStatus() throws Exception {
        UpdateProjectStatusCommand command = new UpdateProjectStatusCommand(ProjectStatus.IN_PROGRESS);

        when(projectService.updateStatus(any(), any())).thenReturn(null);

        mockMvc.perform(patch("/api/v1/projects/" + UUID.randomUUID() + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createMilestone_CreatesMilestone() throws Exception {
        CreateMilestoneCommand command = new CreateMilestoneCommand(
                "Milestone 1",
                LocalDate.now().plusWeeks(2),
                MilestoneStatus.PENDING
        );

        when(projectService.createMilestone(any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/milestones")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void list_ClientForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isForbidden());
    }
}
