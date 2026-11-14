package com.growthprint.backend.auth.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules for every NEW password (reset, change, invitation). Existing passwords keep working.
 * Follows NIST SP 800-63B: length matters most, so 12+ characters, plus a letter and a digit, no obvious
 * values and nothing derived from the email address. The frontend shows the same rules
 * (keep {@link #DESCRIPTION} and the frontend copy in sync).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /** BCrypt only uses the first 72 bytes; 128 chars caps request size while allowing passphrases. */
    public static final int MAX_LENGTH = 128;

    public static final String DESCRIPTION = "At least " + MIN_LENGTH + " characters, including a letter and a number. "
            + "Avoid common passwords and your email address.";

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "123456789012", "qwertyuiop", "qwerty123456",
            "letmein", "welcome", "welcome123", "iloveyou", "admin", "admin123", "changeme", "changeme123",
            "growthprint", "growthprint123", "abc123456789", "passw0rd", "p@ssw0rd", "11111111111");

    private PasswordPolicy() {
    }

    /** @return human-readable violations; empty when the password is acceptable */
    public static List<String> violations(String password, String email) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            problems.add("Password is required");
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("Use at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("Use at most " + MAX_LENGTH + " characters");
        }
        if (password.chars().noneMatch(Character::isLetter)) {
            problems.add("Include at least one letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Include at least one number");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String collapsed = lower.replaceAll("[^a-z0-9]", "");
        if (COMMON.contains(lower) || COMMON.contains(collapsed) || password.chars().distinct().count() <= 2) {
            problems.add("This password is too common or too simple");
        }
        if (email != null) {
            String localPart = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
            if (localPart.length() >= 3 && lower.contains(localPart)) {
                problems.add("Do not include your email address");
            }
        }
        return problems;
    }
}
