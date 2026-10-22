package com.growthprint.backend.project.controller;

import com.growthprint.backend.project.dto.CreateMilestoneCommand;
import com.growthprint.backend.project.dto.CreateProjectCommand;
import com.growthprint.backend.project.dto.MilestoneResponse;
import com.growthprint.backend.project.dto.ProjectResponse;
import com.growthprint.backend.project.dto.UpdateProjectStatusCommand;
import com.growthprint.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.springframework.web.bind.annotation.RequestMethod.PATCH;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @Operation(summary = "List all projects")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProjectResponse> list() {
        return projectService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse getById(@PathVariable UUID id) {
        return projectService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a new project")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse create(@Valid @RequestBody CreateProjectCommand command) {
        return projectService.create(command);
    }

    @RequestMapping(value = "/{id}/status", method = PATCH)
    @Operation(summary = "Update project status")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectStatusCommand command) {
        return projectService.updateStatus(id, command);
    }

    @PostMapping("/{projectId}/milestones")
    @Operation(summary = "Create a milestone for a project")
    @PreAuthorize("hasRole('ADMIN')")
    public MilestoneResponse createMilestone(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMilestoneCommand command) {
        return projectService.createMilestone(projectId, command);
    }
}
