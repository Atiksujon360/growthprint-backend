package com.growthprint.backend.security;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.auth.service.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Token-vs-account checks done on every request, plus the shared password policy and token helpers. */
class AccountSessionServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AccountSessionService service = new AccountSessionService(users);

    private User user(UserRole role) {
        User user = new User(UUID.randomUUID(), "u@example.test", "u@example.test", "hash", role,
                AccountStatus.ACTIVE, role == UserRole.CLIENT ? UUID.randomUUID() : null, Instant.now(), Instant.now());
        when(users.findWithPermissionsById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void activeAccountWithMatchingRoleAndVersionIsAccepted() {
        User user = user(UserRole.ADMIN);
        assertThat(service.authoritiesFor(user.getId(), "ADMIN", 0)).hasValueSatisfying(a ->
                assertThat(a).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN"));
    }

    @Test
    void staleVersionChangedRoleOrInactiveAccountIsRejected() {
        User user = user(UserRole.ADMIN);
        user.revokeAllTokens();
        assertThat(service.authoritiesFor(user.getId(), "ADMIN", 0)).isEmpty();
        assertThat(service.authoritiesFor(user.getId(), "ADMIN", 1)).isPresent();
        assertThat(service.authoritiesFor(user.getId(), "CLIENT", 1)).isEmpty();

        user.changeStatus(AccountStatus.SUSPENDED);
        assertThat(service.authoritiesFor(user.getId(), "ADMIN", user.getTokenVersion())).isEmpty();
        assertThat(service.authoritiesFor(UUID.randomUUID(), "ADMIN", 0)).isEmpty();
    }

    @Test
    void teamMemberAuthoritiesComeFromTheDatabase() {
        User member = user(UserRole.TEAM_MEMBER);
        member.replacePermissions(EnumSet.of(TeamModule.CRM, TeamModule.SUPPORT));
        assertThat(service.authoritiesFor(member.getId(), "TEAM_MEMBER", 0)).hasValueSatisfying(a ->
                assertThat(a).extracting(GrantedAuthority::getAuthority)
                        .containsExactlyInAnyOrder("ROLE_TEAM_MEMBER", "PERM_CRM", "PERM_SUPPORT"));
    }

    @Test
    void passwordPolicy() {
        assertThat(PasswordPolicy.violations("Correct-Horse-42", "owner@example.test")).isEmpty();
        assertThat(PasswordPolicy.violations("short1", null)).isNotEmpty();
        assertThat(PasswordPolicy.violations("onlylettersssss", null)).anyMatch(v -> v.contains("number"));
        assertThat(PasswordPolicy.violations("123456789012345", null)).anyMatch(v -> v.contains("letter"));
        assertThat(PasswordPolicy.violations("Password123", null)).isNotEmpty();
        assertThat(PasswordPolicy.violations("sarahchen-2026!", "sarahchen@example.test"))
                .anyMatch(v -> v.contains("email"));
        assertThat(PasswordPolicy.violations("a".repeat(129) + "1", null)).anyMatch(v -> v.contains("at most"));
    }

    @Test
    void secureTokensAreRandomAndHashed() {
        String a = SecureTokens.newToken();
        String b = SecureTokens.newToken();
        assertThat(a).isNotEqualTo(b).hasSize(43);
        assertThat(SecureTokens.looksValid(a)).isTrue();
        assertThat(SecureTokens.sha256Hex(a)).hasSize(64).isNotEqualTo(a).isEqualTo(SecureTokens.sha256Hex(a));
        assertThat(SecureTokens.looksValid("bad token")).isFalse();
    }
}
