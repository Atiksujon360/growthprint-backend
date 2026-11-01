package com.growthprint.backend.proposal.exception;

/**
 * Thrown when a client decides on a proposal past its validUntil date. The service marks the proposal
 * EXPIRED and must NOT roll that change back, hence {@code noRollbackFor} on the service method.
 * Mapped to 409 by GlobalExceptionHandler.
 */
public class ProposalExpiredException extends RuntimeException {
    public ProposalExpiredException() {
        super("Proposal has expired");
    }
}
