package com.growthprint.backend.crm.lead.exception;

import java.util.UUID;

public class CrmLeadNotFoundException extends RuntimeException {

    public CrmLeadNotFoundException(UUID id) {
        super("CRM lead not found with id: " + id);
    }
}
