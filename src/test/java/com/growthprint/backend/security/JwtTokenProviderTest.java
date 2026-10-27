package com.growthprint.backend.security;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-testing-only-must-be-long-enough-for-hs256");
        jwtProperties.setExpiration(Duration.ofHours(1));
        jwtProperties.setIssuer("growthprint-backend");

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    void generateToken_ValidUser_ReturnsToken() {
        User user = new User(
                UUID.randomUUID(),
                "test@example.com",
                "test@example.com",
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        String token = jwtTokenProvider.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void validateToken_ValidToken_ReturnsTrue() {
        User user = new User(
                UUID.randomUUID(),
                "test@example.com",
                "test@example.com",
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        String token = jwtTokenProvider.generateToken(user);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_InvalidToken_ReturnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    void getUserIdFromToken_ValidToken_ReturnsUserId() {
        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                "test@example.com",
                "test@example.com",
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        String token = jwtTokenProvider.generateToken(user);
        UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        assertEquals(userId, extractedUserId);
    }

    @Test
    void getEmailFromToken_ValidToken_ReturnsEmail() {
        String email = "test@example.com";
        User user = new User(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now()
        );

        String token = jwtTokenProvider.generateToken(user);
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

        assertEquals(email, extractedEmail);
    }

    @Test
    void getRoleFromToken_ValidToken_ReturnsRole() {
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
        String extractedRole = jwtTokenProvider.getRoleFromToken(token);

        assertEquals("CLIENT", extractedRole);
    }

    @Test
    void constructor_NullSecret_ThrowsException() {
        jwtProperties.setSecret(null);

        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(jwtProperties));
    }

    @Test
    void constructor_BlankSecret_ThrowsException() {
        jwtProperties.setSecret("");

        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(jwtProperties));
    }
}
