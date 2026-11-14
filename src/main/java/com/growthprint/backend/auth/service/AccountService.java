package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.dto.AccountDtos.UpdateProfileRequest;
import com.growthprint.backend.auth.dto.LoginResponse;
import com.growthprint.backend.auth.dto.UserProfileResponse;
import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.exception.CurrentPasswordMismatchException;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.auth.exception.WeakPasswordException;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.security.JwtTokenProvider;
import com.growthprint.backend.security.SecurityAudit;
import com.growthprint.backend.security.SecurityAudit.Event;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Self-service account operations for every role, plus issuing session tokens. */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /** Called after a successful credential check: records the login and issues an access token. */
    @Transactional
    public LoginResponse startSession(UUID userId) {
        User user = load(userId);
        user.recordLogin(clock.instant());
        SecurityAudit.record(Event.LOGIN_SUCCEEDED, userId, userId, user.getRole().name());
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(UUID userId) {
        return UserProfileResponse.from(load(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        String timezone = blankToNull(request.timezone());
        if (timezone != null) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Time zone is not recognised");
            }
        }
        user.updateProfile(
                blankToNull(request.fullName()),
                blankToNull(request.phone()),
                blankToNull(request.jobTitle()),
                timezone,
                request.emailNotifications() == null ? user.isEmailNotifications() : request.emailNotifications());
        SecurityAudit.record(Event.PROFILE_UPDATED, userId, userId, null);
        return UserProfileResponse.from(user);
    }

    /**
     * Verifies the current password, applies the policy, stores the new hash and revokes every other
     * session. Returns a fresh token for the current device so the user stays signed in here.
     */
    @Transactional(noRollbackFor = CurrentPasswordMismatchException.class)
    public LoginResponse changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findForUpdateById(userId)
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            SecurityAudit.record(Event.PASSWORD_CHANGE_REJECTED, userId, userId, "wrong-current-password");
            throw new CurrentPasswordMismatchException();
        }
        List<String> problems = PasswordPolicy.violations(newPassword, user.getEmail());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            problems = new java.util.ArrayList<>(problems);
            problems.add("Choose a password you are not using now");
        }
        if (!problems.isEmpty()) {
            throw new WeakPasswordException("newPassword", problems);
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword), clock.instant());
        SecurityAudit.record(Event.PASSWORD_CHANGED, userId, userId, "other-sessions-revoked");
        return issue(user);
    }

    /** "Sign out everywhere": every token issued so far, including the caller's, stops working. */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        User user = userRepository.findForUpdateById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.revokeAllTokens();
        SecurityAudit.record(Event.SESSIONS_REVOKED, userId, userId, "self");
    }

    private LoginResponse issue(User user) {
        String token = jwtTokenProvider.generateToken(user);
        Instant expiresAt = Instant.now().plus(jwtProperties.getExpiration());
        LoginResponse.UserProfile profile = new LoginResponse.UserProfile(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name(),
                user.getFullName(),
                user.getPermissions().stream().map(Enum::name).sorted().toList());
        return new LoginResponse(token, "Bearer", expiresAt, profile);
    }

    private User load(UUID userId) {
        return userRepository.findWithPermissionsById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
