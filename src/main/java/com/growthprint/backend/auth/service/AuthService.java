package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.exception.AuthenticationFailedException;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Hash of a random throwaway password. Compared against when the user does not exist so that
     * "unknown email" and "wrong password" take the same time (prevents account enumeration).
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT).trim();

        Optional<User> user = userRepository.findByNormalizedEmailAndAccountStatus(normalizedEmail, AccountStatus.ACTIVE);

        if (user.isEmpty()) {
            // Burn the same BCrypt cost as a real check, then fail with the same generic message.
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new AuthenticationFailedException("Invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.get().getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        return user.get();
    }

    /** Stores the last sign-in time (shown in account and user-management screens). */
    @Transactional
    public void recordSuccessfulLogin(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> user.recordLogin(java.time.Instant.now()));
    }

    @Transactional(readOnly = true)
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT).trim();
        return userRepository.findByNormalizedEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
