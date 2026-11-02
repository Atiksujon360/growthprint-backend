package com.growthprint.backend.business.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.InvalidAccountTokenException;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.business.entity.Business;
import com.growthprint.backend.business.entity.BusinessInvitation;
import com.growthprint.backend.business.entity.BusinessMembership;
import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessAccessDeniedException;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessNotFoundException;
import com.growthprint.backend.business.repository.BusinessInvitationRepository;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import com.growthprint.backend.config.AccountSecurityProperties;
import com.growthprint.backend.security.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Membership rules, against an in-memory membership table. Covers: non-members get 404, role ranks, owner-only
 * owner changes, last-owner protection, ownership transfer, and invitation acceptance bound to the email.
 */
class BusinessMembershipServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private final UUID businessId = UUID.randomUUID();
    private final UUID otherBusinessId = UUID.randomUUID();

    private final Map<String, BusinessMembership> table = new HashMap<>();
    private BusinessMembershipRepository memberships;
    private BusinessInvitationRepository invitations;
    private UserRepository users;
    private BusinessMembershipService service;

    private final UUID owner = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        Business business = new Business(businessId, "Acme", "acme", owner, NOW);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(businesses.findForUpdateById(businessId)).thenReturn(Optional.of(business));
        when(businesses.findById(otherBusinessId)).thenReturn(Optional.of(new Business(otherBusinessId, "Other",
                "other", outsider, NOW)));

        memberships = mock(BusinessMembershipRepository.class);
        when(memberships.findActive(any(), any())).thenAnswer(i -> Optional.ofNullable(
                table.get(key(i.getArgument(0), i.getArgument(1)))).filter(BusinessMembership::isActive));
        when(memberships.findOne(any(), any())).thenAnswer(i -> Optional.ofNullable(
                table.get(key(i.getArgument(0), i.getArgument(1)))));
        when(memberships.countActiveWithRole(any(), any())).thenAnswer(i -> table.values().stream()
                .filter(m -> m.getBusinessId().equals(i.getArgument(0)) && m.isActive()
                        && m.getRole() == i.getArgument(1)).count());
        when(memberships.save(any())).thenAnswer(i -> {
            BusinessMembership m = i.getArgument(0);
            table.put(key(m.getBusinessId(), m.getUserId()), m);
            return m;
        });
        add(owner, BusinessRole.OWNER);
        add(admin, BusinessRole.ADMIN);
        add(manager, BusinessRole.MANAGER);
        add(member, BusinessRole.MEMBER);
        table.put(key(otherBusinessId, outsider), new BusinessMembership(otherBusinessId, outsider,
                BusinessRole.OWNER, null, NOW));

        invitations = mock(BusinessInvitationRepository.class);
        when(invitations.findPending(any(), anyString())).thenReturn(List.of());
        when(invitations.save(any())).thenAnswer(i -> i.getArgument(0));
        users = mock(UserRepository.class);
        when(users.findById(any())).thenReturn(Optional.empty());
        when(users.findByNormalizedEmail(anyString())).thenReturn(Optional.empty());

        AccountSecurityProperties props = new AccountSecurityProperties();
        props.setFrontendBaseUrl("https://app.example.test");
        service = new BusinessMembershipService(businesses, memberships, invitations, users,
                new BusinessAccessService(businesses, memberships), props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String key(Object b, Object u) {
        return b + "/" + u;
    }

    private void add(UUID user, BusinessRole role) {
        table.put(key(businessId, user), new BusinessMembership(businessId, user, role, null, NOW));
    }

    private BusinessRole roleOf(UUID user) {
        return table.get(key(businessId, user)).getRole();
    }

    @Test
    void nonMembersAndOtherTenantsGetNotFound() {
        assertThatThrownBy(() -> service.listMembers(businessId, outsider)).isInstanceOf(BusinessNotFoundException.class);
        assertThatThrownBy(() -> service.changeRole(businessId, outsider, member, BusinessRole.ADMIN))
                .isInstanceOf(BusinessNotFoundException.class);
        // A member of business A cannot act inside business B
        assertThatThrownBy(() -> service.listMembers(otherBusinessId, owner)).isInstanceOf(BusinessNotFoundException.class);
        assertThatThrownBy(() -> service.listMembers(UUID.randomUUID(), owner)).isInstanceOf(BusinessNotFoundException.class);
    }

    @Test
    void membersAndManagersCannotManageRoles() {
        assertThatThrownBy(() -> service.changeRole(businessId, member, manager, BusinessRole.MEMBER))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.changeRole(businessId, manager, member, BusinessRole.MANAGER))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void adminsManageOnlyRolesBelowThemAndNeverOwners() {
        service.changeRole(businessId, admin, member, BusinessRole.MANAGER);
        assertThat(roleOf(member)).isEqualTo(BusinessRole.MANAGER);

        assertThatThrownBy(() -> service.changeRole(businessId, admin, manager, BusinessRole.ADMIN))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.changeRole(businessId, admin, member, BusinessRole.OWNER))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.changeRole(businessId, admin, owner, BusinessRole.MEMBER))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.removeMember(businessId, admin, owner))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void lastOwnerCannotBeDemotedRemovedOrLeave() {
        assertThatThrownBy(() -> service.changeRole(businessId, owner, owner, BusinessRole.ADMIN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.removeMember(businessId, owner, owner))
                .isInstanceOf(IllegalStateException.class);
        assertThat(roleOf(owner)).isEqualTo(BusinessRole.OWNER);
    }

    @Test
    void ownerCanPromoteAnotherOwnerAndThenStepDown() {
        service.changeRole(businessId, owner, admin, BusinessRole.OWNER);
        service.changeRole(businessId, owner, owner, BusinessRole.ADMIN);
        assertThat(roleOf(admin)).isEqualTo(BusinessRole.OWNER);
        assertThat(roleOf(owner)).isEqualTo(BusinessRole.ADMIN);
    }

    @Test
    void onlyTheCurrentOwnerCanTransferOwnership() {
        assertThatThrownBy(() -> service.transferOwnership(businessId, admin, member))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.transferOwnership(businessId, owner, outsider))
                .isInstanceOf(BusinessNotFoundException.class);

        when(users.findById(manager)).thenReturn(Optional.of(user(manager, "m@example.test", UserRole.TEAM_MEMBER)));
        service.transferOwnership(businessId, owner, manager);
        assertThat(roleOf(manager)).isEqualTo(BusinessRole.OWNER);
        assertThat(roleOf(owner)).isEqualTo(BusinessRole.ADMIN);
    }

    @Test
    void ownershipCannotGoToAMemberWhoseAccountIsNotActive() {
        User suspended = user(member, "s@example.test", UserRole.TEAM_MEMBER);
        suspended.changeStatus(AccountStatus.SUSPENDED);
        when(users.findById(member)).thenReturn(Optional.of(suspended));
        assertThatThrownBy(() -> service.transferOwnership(businessId, owner, member))
                .isInstanceOf(IllegalStateException.class);
        assertThat(roleOf(owner)).isEqualTo(BusinessRole.OWNER);
        assertThat(roleOf(member)).isEqualTo(BusinessRole.MEMBER);
    }

    @Test
    void acceptingAsAnExistingMemberClosesTheInvitationWithoutChangingRole() {
        String raw = SecureTokens.newToken();
        BusinessInvitation stored = new BusinessInvitation(UUID.randomUUID(), businessId, "mem@example.test",
                BusinessRole.ADMIN, SecureTokens.sha256Hex(raw), NOW.plusSeconds(3600), owner, NOW);
        when(invitations.findForUpdateByTokenHash(SecureTokens.sha256Hex(raw))).thenReturn(Optional.of(stored));
        when(users.findById(member)).thenReturn(Optional.of(user(member, "mem@example.test", UserRole.TEAM_MEMBER)));

        var result = service.acceptInvitation(member, raw);

        assertThat(result.myRole()).isEqualTo("MEMBER");
        assertThat(roleOf(member)).isEqualTo(BusinessRole.MEMBER);
        assertThat(stored.getStatus()).isEqualTo(BusinessInvitation.ACCEPTED);
        assertThatThrownBy(() -> service.acceptInvitation(member, raw)).isInstanceOf(InvalidAccountTokenException.class);
    }

    @Test
    void noRoleCanEscalateItself() {
        assertThatThrownBy(() -> service.changeRole(businessId, admin, admin, BusinessRole.OWNER))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.changeRole(businessId, manager, manager, BusinessRole.ADMIN))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.changeRole(businessId, member, member, BusinessRole.MANAGER))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThat(roleOf(admin)).isEqualTo(BusinessRole.ADMIN);
    }

    @Test
    void membersMayLeaveButNotRemoveOthers() {
        assertThatThrownBy(() -> service.removeMember(businessId, member, manager))
                .isInstanceOf(BusinessAccessDeniedException.class);
        service.removeMember(businessId, member, member);
        assertThat(table.get(key(businessId, member)).isActive()).isFalse();
        // Removed members immediately lose access
        assertThatThrownBy(() -> service.listMembers(businessId, member)).isInstanceOf(BusinessNotFoundException.class);
    }

    @Test
    void invitationsRespectRanksAndAreBoundToTheInvitedEmail() {
        assertThatThrownBy(() -> service.invite(businessId, admin, "x@example.test", BusinessRole.ADMIN))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThatThrownBy(() -> service.invite(businessId, owner, "x@example.test", BusinessRole.OWNER))
                .isInstanceOf(IllegalArgumentException.class);

        var created = service.invite(businessId, admin, "New.Person@Example.test", BusinessRole.MEMBER);
        assertThat(created.link()).isEqualTo("https://app.example.test/join-business#token=" + created.token());
        assertThat(created.invitation().email()).isEqualTo("new.person@example.test");

        BusinessInvitation stored = new BusinessInvitation(created.invitation().id(), businessId,
                "new.person@example.test", BusinessRole.MEMBER, SecureTokens.sha256Hex(created.token()),
                NOW.plusSeconds(3600), admin, NOW);
        when(invitations.findForUpdateByTokenHash(SecureTokens.sha256Hex(created.token())))
                .thenReturn(Optional.of(stored));

        UUID wrongId = UUID.randomUUID();
        when(users.findById(wrongId)).thenReturn(Optional.of(user(wrongId, "someone.else@example.test", UserRole.TEAM_MEMBER)));
        assertThatThrownBy(() -> service.acceptInvitation(wrongId, created.token()))
                .isInstanceOf(InvalidAccountTokenException.class);

        UUID invitee = UUID.randomUUID();
        when(users.findById(invitee)).thenReturn(Optional.of(user(invitee, "new.person@example.test", UserRole.TEAM_MEMBER)));
        var joined = service.acceptInvitation(invitee, created.token());
        assertThat(joined.myRole()).isEqualTo("MEMBER");
        assertThat(stored.getStatus()).isEqualTo(BusinessInvitation.ACCEPTED);

        // Single use
        assertThatThrownBy(() -> service.acceptInvitation(invitee, created.token()))
                .isInstanceOf(InvalidAccountTokenException.class);
    }

    private static User user(UUID id, String email, UserRole role) {
        return new User(id, email, email, "hash", role, AccountStatus.ACTIVE, null, NOW, NOW);
    }
}
