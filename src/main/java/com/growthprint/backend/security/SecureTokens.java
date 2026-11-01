package com.growthprint.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-time tokens for password reset and invitation links.
 * Raw tokens carry 256 bits from {@link SecureRandom} and are only ever placed in the link; the database
 * keeps their SHA-256 hex digest. SHA-256 (not BCrypt) is appropriate here because the input is a
 * high-entropy random value, not a human-chosen secret, and lookups must be by hash.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    /** Upper bound for submitted tokens: a 32-byte base64url token is 43 chars. */
    public static final int MAX_TOKEN_LENGTH = 128;

    private SecureTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Cheap shape check before touching the database. */
    public static boolean looksValid(String rawToken) {
        return rawToken != null && !rawToken.isBlank() && rawToken.length() <= MAX_TOKEN_LENGTH
                && rawToken.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_');
    }
}
