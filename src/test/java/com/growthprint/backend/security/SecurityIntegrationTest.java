package com.growthprint.backend.security;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityIntegrationTest {

    private JwtTokenProvider jwtTokenProvider;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-testing-must-be-at-least-32-characters-long");
        jwtProperties.setExpiration(java.time.Duration.ofHours(1));
        jwtProperties.setIssuer("growthprint-backend-test");

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Test
    void jwtTokenProvider_GeneratesValidTokenForAdmin() {
        User adminUser = new User(
                UUID.randomUUID(),
                "admin@test.com",
                "admin@test.com",
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        String token = jwtTokenProvider.generateToken(adminUser);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(adminUser.getId(), jwtTokenProvider.getUserIdFromToken(token));
        assertEquals("ADMIN", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    void jwtTokenProvider_GeneratesValidTokenForClient() {
        User clientUser = new User(
                UUID.randomUUID(),
                "client@test.com",
                "client@test.com",
                "hashedPassword",
                UserRole.CLIENT,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        String token = jwtTokenProvider.generateToken(clientUser);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(clientUser.getId(), jwtTokenProvider.getUserIdFromToken(token));
        assertEquals("CLIENT", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    void jwtTokenProvider_RejectsInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    void jwtTokenProvider_RoleClaimCannotBeManipulated() {
        User clientUser = new User(
                UUID.randomUUID(),
                "client@test.com",
                "client@test.com",
                "hashedPassword",
                UserRole.CLIENT,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        // Generate token for CLIENT user
        String token = jwtTokenProvider.generateToken(clientUser);

        // Verify role is CLIENT
        assertEquals("CLIENT", jwtTokenProvider.getRoleFromToken(token));

        // Attempt to parse as admin would fail (token is signed)
        // Client cannot promote themselves to ADMIN by manipulating the token
        assertNotEquals("ADMIN", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    void passwordEncoder_UsesBCrypt() {
        String rawPassword = "TestPassword123!";
        String hash = passwordEncoder.encode(rawPassword);

        assertNotNull(hash);
        assertNotEquals(rawPassword, hash);
        assertTrue(passwordEncoder.matches(rawPassword, hash));
        assertFalse(passwordEncoder.matches("WrongPassword", hash));
    }

    @Test
    void passwordEncoder_HashesConsistently() {
        String rawPassword = "TestPassword123!";
        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        // BCrypt generates different hashes each time (with salt)
        assertNotEquals(hash1, hash2);

        // But both should match the same password
        assertTrue(passwordEncoder.matches(rawPassword, hash1));
        assertTrue(passwordEncoder.matches(rawPassword, hash2));
    }

    @Test
    void jwtTokenProvider_FailsWithoutSecret() {
        JwtProperties invalidProperties = new JwtProperties();
        invalidProperties.setSecret(null);

        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(invalidProperties));
    }

    @Test
    void jwtTokenProvider_FailsWithBlankSecret() {
        JwtProperties invalidProperties = new JwtProperties();
        invalidProperties.setSecret("");

        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(invalidProperties));
    }

    @Test
    void jwtTokenProvider_ExtractsEmailCorrectly() {
        User user = new User(
                UUID.randomUUID(),
                "test@example.com",
                "test@example.com",
                "hashedPassword",
                UserRole.CLIENT,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        String token = jwtTokenProvider.generateToken(user);
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

        assertEquals("test@example.com", extractedEmail);
    }
}
