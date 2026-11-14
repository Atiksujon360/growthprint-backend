package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.PasswordResetToken;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.InvalidAccountTokenException;
import com.growthprint.backend.auth.exception.WeakPasswordException;
import com.growthprint.backend.auth.notification.AccountEmailSender;
import com.growthprint.backend.auth.repository.PasswordResetTokenRepository;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.config.AccountSecurityProperties;
import com.growthprint.backend.config.InMemoryRateLimiter;
import com.growthprint.backend.security.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final String STRONG = "Correct-Horse-42-Battery";

    private UserRepository users;
    private PasswordResetTokenRepository tokens;
    private AccountEmailSender emailSender;
    private PasswordEncoder encoder;
    private AccountSecurityProperties properties;
    private PasswordRecoveryService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(PasswordResetTokenRepository.class);
        emailSender = mock(AccountEmailSender.class);
        encoder = new BCryptPasswordEncoder(4);
        properties = new AccountSecurityProperties();
        properties.setFrontendBaseUrl("https://app.example.test/");
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new PasswordRecoveryService(users, tokens, encoder, emailSender, properties,
                new InMemoryRateLimiter(), clock);
    }

    private User activeUser() {
        return new User(UUID.randomUUID(), "Owner@Example.test", "owner@example.test", encoder.encode("Old-password-1"),
                UserRole.ADMIN, AccountStatus.ACTIVE, null, NOW, NOW);
    }

    @Test
    void unknownEmailIsSilentlyIgnored() {
        when(users.findByNormalizedEmailAndAccountStatus(anyString(), any())).thenReturn(Optional.empty());

        service.requestReset("nobody@example.test");

        verify(emailSender, never()).sendPasswordReset(any(), any(), any(), any());
        verify(tokens, never()).save(any());
    }

    @Test
    void knownEmailStoresOnlyAHashAndSendsAFragmentLink() {
        User user = activeUser();
        when(users.findByNormalizedEmailAndAccountStatus("owner@example.test", AccountStatus.ACTIVE))
                .thenReturn(Optional.of(user));

        service.requestReset("  OWNER@example.test ");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        verify(tokens).deleteUnusedByUserId(user.getId());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordReset(eq(user.getEmail()), any(), link.capture(), eq(Duration.ofMinutes(30)));

        assertThat(link.getValue()).startsWith("https://app.example.test/reset-password#token=");
        String rawToken = link.getValue().substring(link.getValue().indexOf("=") + 1);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SecureTokens.sha256Hex(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void perAccountThrottleStopsSendingButStaysSilent() {
        User user = activeUser();
        when(users.findByNormalizedEmailAndAccountStatus(anyString(), any())).thenReturn(Optional.of(user));
        for (int i = 0; i < 5; i++) {
            service.requestReset("owner@example.test");
        }
        verify(emailSender, org.mockito.Mockito.times(3)).sendPasswordReset(any(), any(), any(), any());
    }

    @Test
    void validTokenResetsPasswordOnceAndRevokesSessions() {
        User user = activeUser();
        int versionBefore = user.getTokenVersion();
        String raw = SecureTokens.newToken();
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), user.getId(),
                SecureTokens.sha256Hex(raw), NOW.minusSeconds(60), NOW.plusSeconds(600));
        when(tokens.findForUpdateByTokenHash(SecureTokens.sha256Hex(raw))).thenReturn(Optional.of(token));
        when(users.findForUpdateById(user.getId())).thenReturn(Optional.of(user));

        service.resetPassword(raw, STRONG);

        assertThat(encoder.matches(STRONG, user.getPasswordHash())).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(versionBefore + 1);
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(token.getUsedAt()).isEqualTo(NOW);

        // Reusing the same link fails
        assertThatThrownBy(() -> service.resetPassword(raw, STRONG + "x"))
                .isInstanceOf(InvalidAccountTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String raw = SecureTokens.newToken();
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), UUID.randomUUID(),
                SecureTokens.sha256Hex(raw), NOW.minusSeconds(3600), NOW.minusSeconds(1));
        when(tokens.findForUpdateByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(raw, STRONG)).isInstanceOf(InvalidAccountTokenException.class);
        verify(users, never()).findForUpdateById(any());
    }

    @Test
    void unknownOrMalformedTokenIsRejected() {
        when(tokens.findForUpdateByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword(SecureTokens.newToken(), STRONG))
                .isInstanceOf(InvalidAccountTokenException.class);
        assertThatThrownBy(() -> service.resetPassword("not a token!", STRONG))
                .isInstanceOf(InvalidAccountTokenException.class);
        assertThatThrownBy(() -> service.verify("x".repeat(500)))
                .isInstanceOf(InvalidAccountTokenException.class);
    }

    @Test
    void tokenOfDeactivatedAccountIsRejected() {
        User user = activeUser();
        user.changeStatus(AccountStatus.DISABLED);
        String raw = SecureTokens.newToken();
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), user.getId(),
                SecureTokens.sha256Hex(raw), NOW, NOW.plusSeconds(600));
        when(tokens.findForUpdateByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(users.findForUpdateById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(raw, STRONG)).isInstanceOf(InvalidAccountTokenException.class);
        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void weakPasswordKeepsTheTokenUsable() {
        User user = activeUser();
        String raw = SecureTokens.newToken();
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), user.getId(),
                SecureTokens.sha256Hex(raw), NOW, NOW.plusSeconds(600));
        when(tokens.findForUpdateByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(users.findForUpdateById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(raw, "short1")).isInstanceOf(WeakPasswordException.class);
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.isUsable(NOW)).isTrue();
    }
}
