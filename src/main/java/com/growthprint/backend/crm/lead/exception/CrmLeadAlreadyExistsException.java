package com.growthprint.backend.crm.lead.exception;

public class CrmLeadAlreadyExistsException extends RuntimeException {

    public CrmLeadAlreadyExistsException(String message) {
        super(message);
    }
}
