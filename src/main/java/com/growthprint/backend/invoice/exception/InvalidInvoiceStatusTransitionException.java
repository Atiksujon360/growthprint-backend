package com.growthprint.backend.invoice.exception;

public class InvalidInvoiceStatusTransitionException extends RuntimeException {
    public InvalidInvoiceStatusTransitionException(String message) {
        super(message);
    }
}
