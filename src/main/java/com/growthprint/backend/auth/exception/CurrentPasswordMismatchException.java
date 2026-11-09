package com.growthprint.backend.auth.exception;

/**
 * Change-password request with a wrong current password. Mapped to 400 (not 401) so the client does not treat
 * it as an expired session and sign the user out.
 */
public class CurrentPasswordMismatchException extends RuntimeException {

    public CurrentPasswordMismatchException() {
        super("Current password is incorrect");
    }
}
