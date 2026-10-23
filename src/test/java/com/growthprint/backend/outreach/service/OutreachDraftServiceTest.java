package com.growthprint.backend.outreach.service;

import com.growthprint.backend.outreach.entity.OutreachDraft;
import com.growthprint.backend.outreach.entity.OutreachDraftStatus;
import com.growthprint.backend.outreach.exception.InvalidOutreachStatusTransitionException;
import com.growthprint.backend.outreach.exception.OutreachDraftNotFoundException;
import com.growthprint.backend.outreach.repository.OutreachDraftRepository;
import com.growthprint.backend.project.entity.ServiceCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutreachDraftServiceTest {

    @Mock
    private OutreachDraftRepository outreachDraftRepository;

    @InjectMocks
    private OutreachDraftService outreachDraftService;

    private OutreachDraft testDraft;

    @BeforeEach
    void setUp() {
        testDraft = new OutreachDraft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Company",
                "John Doe",
                "john@example.com",
                "Test Subject",
                "Test message body",
                "Evidence of personalization",
                ServiceCategory.BUSINESS_AUTOMATION,
                "[]",
                OutreachDraftStatus.DRAFT,
                false,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void findAll_ReturnsAllDrafts() {
        when(outreachDraftRepository.findAll()).thenReturn(List.of(testDraft));

        var result = outreachDraftService.findAll();

        assertEquals(1, result.size());
        assertEquals("Test Company", result.get(0).companyName());
    }

    @Test
    void findById_ReturnsDraft() {
        when(outreachDraftRepository.findById(testDraft.getId())).thenReturn(Optional.of(testDraft));

        var result = outreachDraftService.findById(testDraft.getId());

        assertEquals("Test Company", result.companyName());
        assertEquals("John Doe", result.recipientName());
    }

    @Test
    void findById_ThrowsWhenNotFound() {
        when(outreachDraftRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(OutreachDraftNotFoundException.class, () -> outreachDraftService.findById(UUID.randomUUID()));
    }

    @Test
    void approve_UpdatesStatusToApproved() {
        when(outreachDraftRepository.findById(testDraft.getId())).thenReturn(Optional.of(testDraft));
        when(outreachDraftRepository.save(any())).thenReturn(testDraft);

        var result = outreachDraftService.approve(testDraft.getId());

        assertEquals(OutreachDraftStatus.APPROVED, testDraft.getStatus());
        assertTrue(testDraft.getApprovedByFounder());
        assertNotNull(testDraft.getApprovedAt());
        verify(outreachDraftRepository).save(testDraft);
    }

    @Test
    void approve_ThrowsWhenAlreadySent() {
        testDraft.setStatus(OutreachDraftStatus.SENT);
        when(outreachDraftRepository.findById(testDraft.getId())).thenReturn(Optional.of(testDraft));

        assertThrows(InvalidOutreachStatusTransitionException.class, () -> outreachDraftService.approve(testDraft.getId()));
    }

    @Test
    void approve_ThrowsWhenRejected() {
        testDraft.setStatus(OutreachDraftStatus.REJECTED);
        when(outreachDraftRepository.findById(testDraft.getId())).thenReturn(Optional.of(testDraft));

        assertThrows(InvalidOutreachStatusTransitionException.class, () -> outreachDraftService.approve(testDraft.getId()));
    }

    @Test
    void send_UpdatesStatusToSent() {
        testDraft.setStatus(OutreachDraftStatus.APPROVED);
        when(outreachDraftRepository.findById(testDraft.getId())).thenReturn(Optional.of(testDraft));
        when(outreachDraftRepository.save(any())).thenReturn(testDraft);

        var result = outreachDraftService.send(testDraft.getId());

        assertEquals(OutreachDraftStatus.SENT, testDraft.getStatus());
        assertNotNull(testDraft.getSentAt());
        verify(outreachDraftRepository).save(testDraft);
    }

    @Test
    void send_ThrowsWhenNotApproved() {
        testDraft.setStatus(OutreachDraftStatus.DRAFT);
        when(outreachDraftRepository.findById(testDraft.getId())).thenReturn(Optional.of(testDraft));

        assertThrows(InvalidOutreachStatusTransitionException.class, () -> outreachDraftService.send(testDraft.getId()));
    }

    @Test
    void send_ThrowsWhenNotFound() {
        when(outreachDraftRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(OutreachDraftNotFoundException.class, () -> outreachDraftService.send(UUID.randomUUID()));
    }
}
