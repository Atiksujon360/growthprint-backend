package com.growthprint.backend.auth.exception;

import java.util.List;

/** A new password does not meet {@link com.growthprint.backend.auth.service.PasswordPolicy}. Mapped to 400. */
public class WeakPasswordException extends RuntimeException {

    private final String field;
    private final List<String> violations;

    public WeakPasswordException(String field, List<String> violations) {
        super("Password does not meet the requirements");
        this.field = field;
        this.violations = List.copyOf(violations);
    }

    public String getField() {
        return field;
    }

    public List<String> getViolations() {
        return violations;
    }
}
