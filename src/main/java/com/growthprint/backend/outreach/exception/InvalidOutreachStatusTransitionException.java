package com.growthprint.backend.outreach.exception;

public class InvalidOutreachStatusTransitionException extends RuntimeException {
    public InvalidOutreachStatusTransitionException(String message) {
        super(message);
    }
}
