package com.growthprint.backend.lead.entity;

public final class LeadEnums {

    private LeadEnums() {
    }

    public enum ServiceCategory {
        SOFTWARE_DEV,
        WEBSITE_MAINTENANCE,
        TECHNICAL_SEO,
        BUSINESS_AUTOMATION,
        AI_INTEGRATION
    }

    public enum PreferredContactMethod {
        EMAIL,
        PHONE,
        DISCOVERY_CALL
    }

    public enum LeadStatus {
        RECEIVED
    }
}
