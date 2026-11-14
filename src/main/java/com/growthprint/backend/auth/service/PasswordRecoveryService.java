package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.PasswordResetToken;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.exception.InvalidAccountTokenException;
import com.growthprint.backend.auth.exception.WeakPasswordException;
import com.growthprint.backend.auth.notification.AccountEmailSender;
import com.growthprint.backend.auth.repository.PasswordResetTokenRepository;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.config.AccountSecurityProperties;
import com.growthprint.backend.config.InMemoryRateLimiter;
import com.growthprint.backend.security.SecureTokens;
import com.growthprint.backend.security.SecurityAudit;
import com.growthprint.backend.security.SecurityAudit.Event;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Forgot-password workflow.
 * <ol>
 *     <li>{@link #requestReset}: always succeeds from the caller's view (no account enumeration). For an
 *     ACTIVE account it stores the SHA-256 of a fresh 256-bit token (older unused links are deleted) and hands
 *     the link to {@link AccountEmailSender}.</li>
 *     <li>{@link #verify}: lets the reset page tell the user up front that a link is no longer valid.</li>
 *     <li>{@link #resetPassword}: single use (row lock + used_at), expiry and account state are checked, the
 *     password policy applies, and the token version is bumped so every existing session is signed out.</li>
 * </ol>
 */
@Service
public class PasswordRecoveryService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmailSender emailSender;
    private final AccountSecurityProperties properties;
    private final InMemoryRateLimiter rateLimiter;
    private final Clock clock;

    public PasswordRecoveryService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                   PasswordEncoder passwordEncoder, AccountEmailSender emailSender,
                                   AccountSecurityProperties properties, InMemoryRateLimiter rateLimiter,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Never reveals whether the address belongs to an account; the caller always answers 202. */
    @Transactional
    public void requestReset(String email) {
        Instant now = clock.instant();
        tokenRepository.deleteExpiredBefore(now);

        String normalizedEmail = normalize(email);
        // Per-address throttle (the per-IP limit is in RateLimitFilter). Keyed by hash: no addresses in memory.
        boolean withinLimit = rateLimiter.tryAcquire("reset-account:" + SecureTokens.sha256Hex(normalizedEmail),
                properties.getResetMaxRequestsPerAccount(), properties.getResetAccountWindow());

        Optional<User> account = userRepository.findByNormalizedEmailAndAccountStatus(normalizedEmail,
                AccountStatus.ACTIVE);
        if (account.isEmpty() || !withinLimit) {
            SecurityAudit.record(Event.PASSWORD_RESET_REQUESTED, null, account.map(User::getId).orElse(null),
                    account.isEmpty() ? "no-active-account" : "throttled");
            return;
        }

        User user = account.get();
        String link = issueResetLink(user, now);
        emailSender.sendPasswordReset(user.getEmail(), user.getFullName(), link, properties.getPasswordResetTtl());
        SecurityAudit.record(Event.PASSWORD_RESET_REQUESTED, null, user.getId(),
                emailSender.isConfigured() ? "email-sent" : "email-not-configured");
    }

    /**
     * Creates a new one-time reset link for an ACTIVE account (also used by admins to help a user when email
     * delivery is not configured). Previous unused links stop working.
     */
    @Transactional
    public String issueResetLink(User user, Instant now) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Password reset links can only be issued for active accounts");
        }
        tokenRepository.deleteUnusedByUserId(user.getId());
        String rawToken = SecureTokens.newToken();
        tokenRepository.save(new PasswordResetToken(UUID.randomUUID(), user.getId(),
                SecureTokens.sha256Hex(rawToken), now, now.plus(properties.getPasswordResetTtl())));
        // Fragment (#), not query string: the token is never sent to the frontend host or written to its logs.
        return properties.normalizedFrontendBaseUrl() + "/reset-password#token=" + rawToken;
    }

    @Transactional(readOnly = true)
    public void verify(String rawToken) {
        Instant now = clock.instant();
        PasswordResetToken token = lookup(rawToken, false);
        if (!token.isUsable(now) || !accountCanReset(token.getUserId())) {
            throw new InvalidAccountTokenException();
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = lookup(rawToken, true);
        if (!token.isUsable(now)) {
            SecurityAudit.record(Event.PASSWORD_RESET_REJECTED, null, token.getUserId(),
                    token.getUsedAt() != null ? "reused" : "expired");
            throw new InvalidAccountTokenException();
        }
        User user = userRepository.findForUpdateById(token.getUserId())
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> {
                    SecurityAudit.record(Event.PASSWORD_RESET_REJECTED, null, token.getUserId(), "inactive");
                    return new InvalidAccountTokenException();
                });

        List<String> problems = PasswordPolicy.violations(newPassword, user.getEmail());
        if (!problems.isEmpty()) {
            // The token stays valid so the user can try a stronger password with the same link.
            throw new WeakPasswordException("newPassword", problems);
        }

        token.markUsed(now);
        tokenRepository.deleteUnusedByUserId(user.getId());
        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        SecurityAudit.record(Event.PASSWORD_RESET_COMPLETED, user.getId(), user.getId(), "sessions-revoked");
    }

    private PasswordResetToken lookup(String rawToken, boolean forUpdate) {
        if (!SecureTokens.looksValid(rawToken)) {
            throw new InvalidAccountTokenException();
        }
        String hash = SecureTokens.sha256Hex(rawToken);
        return (forUpdate ? tokenRepository.findForUpdateByTokenHash(hash) : tokenRepository.findByTokenHash(hash))
                .orElseThrow(InvalidAccountTokenException::new);
    }

    private boolean accountCanReset(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .orElse(false);
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
