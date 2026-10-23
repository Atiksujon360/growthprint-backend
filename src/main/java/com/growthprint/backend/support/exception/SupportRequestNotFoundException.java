package com.growthprint.backend.support.exception;

public class SupportRequestNotFoundException extends RuntimeException {
    public SupportRequestNotFoundException(String message) {
        super(message);
    }
}
