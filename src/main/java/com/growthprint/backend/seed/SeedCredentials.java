package com.growthprint.backend.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;

/** Random passwords for the development seed, and the git-ignored file they are written to (never logged). */
final class SeedCredentials {

    private static final SecureRandom RANDOM = new SecureRandom();
    // No look-alike characters (0/O, 1/l/I) so a password can be typed from the file.
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private SeedCredentials() {
    }

    /** 20 random characters, always containing a letter and a digit (meets the password policy). */
    static String randomPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        sb.setCharAt(0, "abcdefghijkmnpqrstuvwxyz".charAt(RANDOM.nextInt(24)));
        sb.setCharAt(1, "23456789".charAt(RANDOM.nextInt(8)));
        return sb.toString();
    }

    /** Writes KEY=value lines, replacing the file. Returns the path written. */
    static Path write(Path file, Map<String, String> values) {
        StringBuilder sb = new StringBuilder("# Development seed accounts. Git-ignored. Regenerated on each seed run.\n");
        new TreeMap<>(values).forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        try {
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
        return file;
    }
}
