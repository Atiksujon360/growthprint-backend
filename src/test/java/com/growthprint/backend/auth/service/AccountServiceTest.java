package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.dto.AccountDtos.UpdateProfileRequest;
import com.growthprint.backend.auth.dto.LoginResponse;
import com.growthprint.backend.auth.dto.UserProfileResponse;
import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.CurrentPasswordMismatchException;
import com.growthprint.backend.auth.exception.WeakPasswordException;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private UserRepository users;
    private PasswordEncoder encoder;
    private JwtTokenProvider tokens;
    private AccountService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        JwtProperties props = new JwtProperties();
        props.setSecret("account-service-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz");
        props.setIssuer("growthprint-backend");
        tokens = new JwtTokenProvider(props);
        service = new AccountService(users, encoder, tokens, props, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User(UUID.randomUUID(), "client@example.test", "client@example.test",
                encoder.encode("Current-pass-123"), UserRole.CLIENT, AccountStatus.ACTIVE, UUID.randomUUID(), NOW, NOW);
        when(users.findForUpdateById(user.getId())).thenReturn(Optional.of(user));
        when(users.findWithPermissionsById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void changePasswordRevokesOldTokensAndIssuesANewOne() {
        String oldToken = tokens.generateToken(user);
        int oldVersion = JwtTokenProvider.tokenVersionOf(tokens.parseValidClaims(oldToken).orElseThrow());

        LoginResponse response = service.changePassword(user.getId(), "Current-pass-123", "Brand-new-pass-456");

        assertThat(encoder.matches("Brand-new-pass-456", user.getPasswordHash())).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(oldVersion + 1);
        int newVersion = JwtTokenProvider.tokenVersionOf(tokens.parseValidClaims(response.accessToken()).orElseThrow());
        assertThat(newVersion).isEqualTo(user.getTokenVersion()).isNotEqualTo(oldVersion);
    }

    @Test
    void wrongCurrentPasswordIsRejectedWithoutChanges() {
        String hashBefore = user.getPasswordHash();
        assertThatThrownBy(() -> service.changePassword(user.getId(), "wrong", "Brand-new-pass-456"))
                .isInstanceOf(CurrentPasswordMismatchException.class);
        assertThat(user.getPasswordHash()).isEqualTo(hashBefore);
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void weakOrReusedNewPasswordIsRejected() {
        assertThatThrownBy(() -> service.changePassword(user.getId(), "Current-pass-123", "short"))
                .isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> service.changePassword(user.getId(), "Current-pass-123", "Current-pass-123"))
                .isInstanceOf(WeakPasswordException.class);
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void signOutEverywhereBumpsTokenVersion() {
        service.revokeAllSessions(user.getId());
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void profileUpdateChangesOnlySelfServiceFields() {
        UserProfileResponse profile = service.updateProfile(user.getId(),
                new UpdateProfileRequest("  Sarah Chen ", "+60 12-345 6789", "", "Asia/Kuala_Lumpur", false));

        assertThat(profile.fullName()).isEqualTo("Sarah Chen");
        assertThat(profile.jobTitle()).isNull();
        assertThat(profile.timezone()).isEqualTo("Asia/Kuala_Lumpur");
        assertThat(profile.emailNotifications()).isFalse();
        assertThat(profile.role()).isEqualTo("CLIENT");
        assertThat(profile.email()).isEqualTo("client@example.test");
    }

    @Test
    void invalidTimezoneIsRejected() {
        assertThatThrownBy(() -> service.updateProfile(user.getId(),
                new UpdateProfileRequest(null, null, null, "Mars/Olympus", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
