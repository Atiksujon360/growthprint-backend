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

    /** Batch read for project lists (one query instead of one per project). */
    List<Milestone> findByProjectIdIn(java.util.Collection<UUID> projectIds);

    List<Milestone> findByProjectIdAndStatus(UUID projectId, MilestoneStatus status);
}
