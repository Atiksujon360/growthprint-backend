package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserInvitation;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.InvalidAccountTokenException;
import com.growthprint.backend.auth.exception.WeakPasswordException;
import com.growthprint.backend.auth.repository.UserInvitationRepository;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.security.SecureTokens;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InvitationPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private UserRepository users;
    private UserInvitationRepository invitations;
    private InvitationService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private User invited;
    private String raw;
    private UserInvitation invitation;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        invitations = mock(UserInvitationRepository.class);
        service = new InvitationService(invitations, users, mock(ClientRepository.class), encoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        invited = new User(UUID.randomUUID(), "new@example.test", "new@example.test", null, UserRole.TEAM_MEMBER,
                AccountStatus.INVITED, null, NOW, NOW);
        invited.assignInvitationDetails("Nia New", null, UUID.randomUUID());
        raw = SecureTokens.newToken();
        invitation = new UserInvitation(UUID.randomUUID(), invited.getId(), SecureTokens.sha256Hex(raw),
                NOW.plusSeconds(3600), null, NOW);
        when(invitations.findByTokenHash(SecureTokens.sha256Hex(raw))).thenReturn(Optional.of(invitation));
        when(invitations.findForUpdateByTokenHash(SecureTokens.sha256Hex(raw))).thenReturn(Optional.of(invitation));
        when(users.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(users.findForUpdateById(invited.getId())).thenReturn(Optional.of(invited));
    }

    @Test
    void previewShowsOnlyGreetingDetails() {
        InvitationPreviewResponse preview = service.preview(raw);
        assertThat(preview.email()).isEqualTo("new@example.test");
        assertThat(preview.fullName()).isEqualTo("Nia New");
        assertThat(preview.role()).isEqualTo("TEAM_MEMBER");
        assertThat(preview.clientName()).isNull();
    }

    @Test
    void acceptActivatesOnceOnly() {
        UUID id = service.accept(raw, "Fresh-Start-2026");

        assertThat(id).isEqualTo(invited.getId());
        assertThat(invited.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(encoder.matches("Fresh-Start-2026", invited.getPasswordHash())).isTrue();
        assertThat(invitation.getStatus()).isEqualTo(UserInvitation.ACCEPTED);
        assertThatThrownBy(() -> service.accept(raw, "Fresh-Start-2026"))
                .isInstanceOf(InvalidAccountTokenException.class);
        assertThatThrownBy(() -> service.preview(raw)).isInstanceOf(InvalidAccountTokenException.class);
    }

    @Test
    void expiredRevokedOrUnknownLinksAreRejected() {
        UserInvitation expired = new UserInvitation(UUID.randomUUID(), invited.getId(), "x", NOW.minusSeconds(1),
                null, NOW.minusSeconds(100));
        when(invitations.findForUpdateByTokenHash(anyString())).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.accept(SecureTokens.newToken(), "Fresh-Start-2026"))
                .isInstanceOf(InvalidAccountTokenException.class);

        invitation.revoke(NOW);
        when(invitations.findForUpdateByTokenHash(SecureTokens.sha256Hex(raw))).thenReturn(Optional.of(invitation));
        assertThatThrownBy(() -> service.accept(raw, "Fresh-Start-2026"))
                .isInstanceOf(InvalidAccountTokenException.class);

        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.preview(SecureTokens.newToken()))
                .isInstanceOf(InvalidAccountTokenException.class);
    }

    @Test
    void weakPasswordKeepsInvitationOpen() {
        assertThatThrownBy(() -> service.accept(raw, "short")).isInstanceOf(WeakPasswordException.class);
        assertThat(invited.getAccountStatus()).isEqualTo(AccountStatus.INVITED);
        assertThat(invitation.getStatus()).isEqualTo(UserInvitation.PENDING);
    }

    @Test
    void alreadyActiveAccountCannotReuseAnInvitation() {
        invited.activateWithPassword("hash", NOW);
        assertThatThrownBy(() -> service.accept(raw, "Fresh-Start-2026"))
                .isInstanceOf(InvalidAccountTokenException.class);
        assertThatThrownBy(() -> service.preview(raw)).isInstanceOf(InvalidAccountTokenException.class);
    }
}
