package com.growthprint.backend.proposal.exception;

import java.util.UUID;

/** Mapped to 404 by GlobalExceptionHandler (previously an IllegalArgumentException, i.e. 400). */
public class ProposalNotFoundException extends RuntimeException {
    public ProposalNotFoundException(UUID id) {
        super("Proposal not found: " + id);
    }
}
