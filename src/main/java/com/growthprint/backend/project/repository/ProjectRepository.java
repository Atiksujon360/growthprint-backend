package com.growthprint.backend.project.repository;

import com.growthprint.backend.project.entity.Project;
import com.growthprint.backend.project.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByClientId(UUID clientId);

    Optional<Project> findByReference(String reference);

    List<Project> findByStatus(ProjectStatus status);

    boolean existsByReference(String reference);
}
