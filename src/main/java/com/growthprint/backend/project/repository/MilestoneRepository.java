package com.growthprint.backend.project.repository;

import com.growthprint.backend.project.entity.Milestone;
import com.growthprint.backend.project.entity.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    List<Milestone> findByProjectId(UUID projectId);

    List<Milestone> findByProjectIdAndStatus(UUID projectId, MilestoneStatus status);
}
