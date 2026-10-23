package com.growthprint.backend.support.exception;

public class InvalidSupportRequestStatusTransitionException extends RuntimeException {
    public InvalidSupportRequestStatusTransitionException(String message) {
        super(message);
    }
}
