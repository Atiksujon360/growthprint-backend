package com.growthprint.backend.client.entity;

public final class ClientEnums {

    private ClientEnums() {
    }

    public enum ClientStatus {
        ACTIVE,
        INVITED,
        SUSPENDED
    }

    public enum RetainerTier {
        ESSENTIAL_MAINTENANCE,
        PRO_GROWTH,
        NONE
    }

    public enum BillingCurrency {
        USD,
        EUR,
        GBP
    }
}
