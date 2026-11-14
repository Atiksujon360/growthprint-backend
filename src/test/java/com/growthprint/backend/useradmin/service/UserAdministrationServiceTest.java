package com.growthprint.backend.useradmin.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserInvitation;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.notification.AccountEmailSender;
import com.growthprint.backend.auth.repository.UserInvitationRepository;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.auth.service.PasswordRecoveryService;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.config.AccountSecurityProperties;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InviteUserRequest;
import com.growthprint.backend.auth.entity.ClientRole;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InvitationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdministrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private UserRepository users;
    private UserInvitationRepository invitations;
    private ClientRepository clients;
    private UserAdministrationService service;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        invitations = mock(UserInvitationRepository.class);
        clients = mock(ClientRepository.class);
        AccountSecurityProperties props = new AccountSecurityProperties();
        props.setFrontendBaseUrl("https://app.example.test");
        JwtProperties jwt = new JwtProperties();
        service = new UserAdministrationService(users, invitations, clients, mock(PasswordRecoveryService.class),
                mock(AccountEmailSender.class), props, jwt, Clock.fixed(NOW, ZoneOffset.UTC));
        when(invitations.findByUserIdAndStatus(any(), any())).thenReturn(List.of());
    }

    private User user(UserRole role, AccountStatus status) {
        return new User(UUID.randomUUID(), role + "@example.test", role.name().toLowerCase() + "@example.test",
                status == AccountStatus.INVITED ? null : "hash", role, status,
                role == UserRole.CLIENT ? UUID.randomUUID() : null, NOW, NOW);
    }

    @Test
    void inviteTeamMemberCreatesInvitedAccountWithHashedLinkToken() {
        when(users.existsByNormalizedEmail("staff@example.test")).thenReturn(false);

        InvitationResponse response = service.invite(adminId, new InviteUserRequest("Staff@Example.test",
                "Sam Staff", UserRole.TEAM_MEMBER, null, null, EnumSet.of(TeamModule.CRM, TeamModule.SUPPORT)));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getAccountStatus()).isEqualTo(AccountStatus.INVITED);
        assertThat(saved.getValue().getPasswordHash()).isNull();
        assertThat(saved.getValue().getPermissions()).containsExactlyInAnyOrder(TeamModule.CRM, TeamModule.SUPPORT);
        assertThat(response.invitation().link())
                .isEqualTo("https://app.example.test/accept-invite#token=" + response.invitation().token());

        ArgumentCaptor<UserInvitation> invitation = ArgumentCaptor.forClass(UserInvitation.class);
        verify(invitations).save(invitation.capture());
        assertThat(invitation.getValue().getStatus()).isEqualTo(UserInvitation.PENDING);
        assertThat(response.user().accountStatus()).isEqualTo("INVITED");
    }

    @Test
    void duplicateEmailAndInvalidCombinationsAreRejected() {
        when(users.existsByNormalizedEmail("dup@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.invite(adminId,
                new InviteUserRequest("dup@example.test", "D", UserRole.ADMIN, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.invite(adminId,
                new InviteUserRequest("c@example.test", "C", UserRole.CLIENT, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.invite(adminId,
                new InviteUserRequest("a@example.test", "A", UserRole.ADMIN, null, null, Set.of(TeamModule.CRM))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clientInviteDefaultsToOwnerOfAnExistingClient() {
        UUID clientId = UUID.randomUUID();
        when(clients.existsById(clientId)).thenReturn(true);

        InvitationResponse response = service.invite(adminId, new InviteUserRequest("owner@acme.test", "Olive",
                UserRole.CLIENT, clientId, null, null));

        assertThat(response.user().clientRole()).isEqualTo(ClientRole.CLIENT_OWNER.name());
        assertThat(response.user().clientId()).isEqualTo(clientId.toString());
    }

    @Test
    void adminCannotChangeOwnStatusOrRole() {
        assertThatThrownBy(() -> service.changeStatus(adminId, adminId, AccountStatus.DISABLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeRole(adminId, adminId, UserRole.TEAM_MEMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lastActiveAdminCannotBeDeactivatedOrDemoted() {
        User otherAdmin = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        when(users.findForUpdateById(otherAdmin.getId())).thenReturn(Optional.of(otherAdmin));
        when(users.countByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeStatus(adminId, otherAdmin.getId(), AccountStatus.SUSPENDED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.changeRole(adminId, otherAdmin.getId(), UserRole.TEAM_MEMBER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(otherAdmin.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void deactivationRevokesSessions() {
        User member = user(UserRole.TEAM_MEMBER, AccountStatus.ACTIVE);
        when(users.findForUpdateById(member.getId())).thenReturn(Optional.of(member));

        service.changeStatus(adminId, member.getId(), AccountStatus.SUSPENDED);

        assertThat(member.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(member.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void clientLoginsCannotBecomeStaffAndInvitedUsersCannotBeForceActivated() {
        User client = user(UserRole.CLIENT, AccountStatus.ACTIVE);
        when(users.findForUpdateById(client.getId())).thenReturn(Optional.of(client));
        assertThatThrownBy(() -> service.changeRole(adminId, client.getId(), UserRole.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);

        User invited = user(UserRole.TEAM_MEMBER, AccountStatus.INVITED);
        when(users.findForUpdateById(invited.getId())).thenReturn(Optional.of(invited));
        assertThatThrownBy(() -> service.changeStatus(adminId, invited.getId(), AccountStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void permissionsOnlyForTeamMembers() {
        User member = user(UserRole.TEAM_MEMBER, AccountStatus.ACTIVE);
        when(users.findForUpdateById(member.getId())).thenReturn(Optional.of(member));
        assertThat(service.replacePermissions(adminId, member.getId(), EnumSet.of(TeamModule.PROJECTS)).permissions())
                .containsExactly("PROJECTS");

        User admin = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        when(users.findForUpdateById(admin.getId())).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> service.replacePermissions(adminId, admin.getId(), EnumSet.of(TeamModule.CRM)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
