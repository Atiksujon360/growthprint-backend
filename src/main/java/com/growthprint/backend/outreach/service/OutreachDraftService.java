package com.growthprint.backend.outreach.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.outreach.dto.OutreachDraftResponse;
import com.growthprint.backend.outreach.entity.OutreachDraft;
import com.growthprint.backend.outreach.entity.OutreachDraftStatus;
import com.growthprint.backend.outreach.exception.InvalidOutreachStatusTransitionException;
import com.growthprint.backend.outreach.exception.OutreachDraftNotFoundException;
import com.growthprint.backend.outreach.repository.OutreachDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OutreachDraftService {

    private final OutreachDraftRepository outreachDraftRepository;
    private final ObjectMapper objectMapper;

    public OutreachDraftService(OutreachDraftRepository outreachDraftRepository) {
        this.outreachDraftRepository = outreachDraftRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public List<OutreachDraftResponse> findAll() {
        List<OutreachDraft> drafts = outreachDraftRepository.findAll();
        return drafts.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OutreachDraftResponse findById(UUID id) {
        OutreachDraft draft = outreachDraftRepository.findById(id)
                .orElseThrow(() -> new OutreachDraftNotFoundException("Outreach draft not found"));
        return toResponse(draft);
    }

    @Transactional
    public OutreachDraftResponse approve(UUID id) {
        OutreachDraft draft = outreachDraftRepository.findById(id)
                .orElseThrow(() -> new OutreachDraftNotFoundException("Outreach draft not found"));

        // Validate status transition
        if (draft.getStatus() == OutreachDraftStatus.SENT) {
            throw new InvalidOutreachStatusTransitionException(
                    "Cannot approve a draft that has already been sent");
        }

        if (draft.getStatus() == OutreachDraftStatus.REJECTED) {
            throw new InvalidOutreachStatusTransitionException(
                    "Cannot approve a rejected draft");
        }

        draft.setStatus(OutreachDraftStatus.APPROVED);
        draft.setApprovedByFounder(true);
        draft.setApprovedAt(Instant.now());
        draft.preUpdate();

        OutreachDraft updatedDraft = outreachDraftRepository.save(draft);
        return toResponse(updatedDraft);
    }

    @Transactional
    public OutreachDraftResponse send(UUID id) {
        OutreachDraft draft = outreachDraftRepository.findById(id)
                .orElseThrow(() -> new OutreachDraftNotFoundException("Outreach draft not found"));

        // Validate status transition
        if (draft.getStatus() != OutreachDraftStatus.APPROVED) {
            throw new InvalidOutreachStatusTransitionException(
                    "Only approved drafts can be sent");
        }

        draft.setStatus(OutreachDraftStatus.SENT);
        draft.setSentAt(Instant.now());
        draft.preUpdate();

        OutreachDraft updatedDraft = outreachDraftRepository.save(draft);
        return toResponse(updatedDraft);
    }

    private OutreachDraftResponse toResponse(OutreachDraft draft) {
        return new OutreachDraftResponse(
                draft.getId().toString(),
                draft.getLeadId().toString(),
                draft.getCompanyName(),
                draft.getRecipientName(),
                draft.getRecipientEmail(),
                draft.getSubject(),
                draft.getMessageBody(),
                draft.getPersonalizationEvidence(),
                draft.getRelevantService().name(),
                deserializeRiskFlags(draft.getRiskFlags()),
                draft.getStatus().name(),
                draft.getApprovedByFounder(),
                draft.getApprovedAt() != null ? draft.getApprovedAt().toString() : null,
                draft.getSentAt() != null ? draft.getSentAt().toString() : null,
                draft.getUpdatedAt().toString()
        );
    }

    private List<String> deserializeRiskFlags(String riskFlagsJson) {
        try {
            if (riskFlagsJson == null || riskFlagsJson.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(riskFlagsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
