package com.growthprint.backend.proposal.repository;

import com.growthprint.backend.proposal.entity.Proposal;
import com.growthprint.backend.proposal.entity.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findByClientId(UUID clientId);
    Optional<Proposal> findByProposalNumber(String proposalNumber);
    List<Proposal> findByStatus(ProposalStatus status);
    boolean existsByProposalNumber(String proposalNumber);
}
