package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.AuthenticationFailedException;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        String normalizedEmail = email.toLowerCase().trim();

        User user = userRepository.findByNormalizedEmailAndAccountStatus(normalizedEmail, AccountStatus.ACTIVE)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        return user;
    }

    @Transactional(readOnly = true)
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        return userRepository.findByNormalizedEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
