package com.growthprint.backend.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips PostgreSQL integration tests (reported as skipped, not passed) when no isolated database exists.
 */
public class PostgresAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (PostgresTestDatabase.isAvailable()) {
            return ConditionEvaluationResult.enabled("Isolated PostgreSQL available");
        }
        return ConditionEvaluationResult.disabled("No isolated PostgreSQL: set " + PostgresTestDatabase.URL_ENV
                + " (+ _USERNAME/_PASSWORD) or start Docker");
    }
}
