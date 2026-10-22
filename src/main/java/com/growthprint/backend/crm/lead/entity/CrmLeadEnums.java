package com.growthprint.backend.crm.lead.entity;

public final class CrmLeadEnums {

    private CrmLeadEnums() {
    }

    public enum CrmLeadSource {
        INBOUND_INQUIRY,
        PUBLIC_DIRECTORY,
        TECH_AUDIT_DISCOVERY,
        REFERRAL
    }

    public enum CrmLeadPipelineStage {
        DISCOVERED,
        RESEARCHING,
        QUALIFIED,
        NEEDS_REVIEW,
        READY_FOR_OUTREACH,
        OUTREACH_APPROVED,
        CONTACTED,
        REPLIED,
        MEETING_SCHEDULED,
        PROPOSAL_SENT,
        WON,
        LOST,
        DO_NOT_CONTACT
    }

    public enum CrmLeadServiceCategory {
        SOFTWARE_DEV,
        WEBSITE_MAINTENANCE,
        TECHNICAL_SEO,
        BUSINESS_AUTOMATION,
        AI_INTEGRATION
    }
}
