package com.growthprint.backend.invoice.pdf;

/** The PDF library failed to produce a document (server-side problem, mapped to HTTP 500). */
public class InvoicePdfGenerationException extends RuntimeException {

    public InvoicePdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
