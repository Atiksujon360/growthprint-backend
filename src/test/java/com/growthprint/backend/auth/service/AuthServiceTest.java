package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.AuthenticationFailedException;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void authenticate_ValidCredentials_ReturnsUser() {
        String email = "test@example.com";
        String password = "password123";
        User user = new User(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        when(userRepository.findByNormalizedEmailAndAccountStatus(email.toLowerCase(), AccountStatus.ACTIVE))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(true);

        User result = authService.authenticate(email, password);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
    }

    @Test
    void authenticate_InvalidPassword_ThrowsException() {
        String email = "test@example.com";
        String password = "wrongPassword";
        User user = new User(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        when(userRepository.findByNormalizedEmailAndAccountStatus(email.toLowerCase(), AccountStatus.ACTIVE))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(email, password));
    }

    @Test
    void authenticate_UserNotFound_ThrowsException() {
        String email = "nonexistent@example.com";
        String password = "password123";

        when(userRepository.findByNormalizedEmailAndAccountStatus(email.toLowerCase(), AccountStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(email, password));
    }

    @Test
    void authenticate_DisabledAccount_ThrowsException() {
        String email = "disabled@example.com";
        String password = "password123";

        when(userRepository.findByNormalizedEmailAndAccountStatus(email.toLowerCase(), AccountStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class, () -> authService.authenticate(email, password));
    }

    @Test
    void getUserById_ValidId_ReturnsUser() {
        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                "test@example.com",
                "test@example.com",
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        User result = authService.getUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
    }

    @Test
    void getUserById_InvalidId_ThrowsException() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> authService.getUserById(userId));
    }

    @Test
    void getUserByEmail_ValidEmail_ReturnsUser() {
        String email = "test@example.com";
        User user = new User(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "hashedPassword",
                UserRole.ADMIN,
                AccountStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        when(userRepository.findByNormalizedEmail(email.toLowerCase())).thenReturn(Optional.of(user));

        User result = authService.getUserByEmail(email);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
    }

    @Test
    void getUserByEmail_InvalidEmail_ThrowsException() {
        String email = "nonexistent@example.com";

        when(userRepository.findByNormalizedEmail(email.toLowerCase())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> authService.getUserByEmail(email));
    }
}
