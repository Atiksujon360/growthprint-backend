package com.growthprint.backend.lead.repository;

import com.growthprint.backend.lead.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    List<Lead> findAllByOrderByReceivedAtDesc();
}
