package com.growthprint.backend.auth.exception;

/**
 * A password-reset or invitation link is unknown, expired, already used or belongs to an account that can no
 * longer use it. One generic message for every case so the response reveals nothing about the account.
 * Mapped to 400.
 */
public class InvalidAccountTokenException extends RuntimeException {

    public static final String MESSAGE = "This link is invalid or has expired. Please request a new one.";

    public InvalidAccountTokenException() {
        super(MESSAGE);
    }
}
