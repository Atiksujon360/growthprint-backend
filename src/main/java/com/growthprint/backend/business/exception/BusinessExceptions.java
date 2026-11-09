package com.growthprint.backend.business.exception;

/** Exceptions of the business (tenant) module; mapped to HTTP in GlobalExceptionHandler. */
public final class BusinessExceptions {

    private BusinessExceptions() {
    }

    /**
     * The business does not exist, is not active, or the caller is not an active member. All three give the
     * same 404 so outsiders cannot discover which business ids exist.
     */
    public static class BusinessNotFoundException extends RuntimeException {
        public BusinessNotFoundException() {
            super("Business not found");
        }
    }

    /** The caller is a member but their role is not high enough for this action (403). */
    public static class BusinessAccessDeniedException extends RuntimeException {
        public BusinessAccessDeniedException(String message) {
            super(message);
        }
    }
}
