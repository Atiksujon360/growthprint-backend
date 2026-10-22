package com.growthprint.backend.crm.lead.exception;

import java.util.UUID;

public class InquiryNotFoundException extends RuntimeException {

    public InquiryNotFoundException(UUID id) {
        super("Inquiry not found with id: " + id);
    }
}
