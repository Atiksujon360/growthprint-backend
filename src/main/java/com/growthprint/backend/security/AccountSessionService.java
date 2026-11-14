package com.growthprint.backend.security;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side check behind every JWT. A signature-valid token is only accepted while
 * <ul>
 *     <li>the account still exists and is ACTIVE (disabling/suspending takes effect immediately),</li>
 *     <li>the role in the token is still the account's role (role changes take effect immediately),</li>
 *     <li>the token version matches users.token_version (password change/reset and "sign out everywhere"
 *     revoke older tokens).</li>
 * </ul>
 * Authorities are loaded from the database, never from the token, so revoked team permissions also apply
 * immediately: {@code ROLE_<role>} plus {@code PERM_<module>} for each module granted to a team member.
 */
@Service
public class AccountSessionService {

    private final UserRepository userRepository;

    public AccountSessionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<List<GrantedAuthority>> authoritiesFor(UUID userId, String tokenRole, int tokenVersion) {
        return userRepository.findWithPermissionsById(userId)
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(user -> user.getRole().name().equals(tokenRole))
                .filter(user -> user.getTokenVersion() == tokenVersion)
                .map(AccountSessionService::authoritiesOf);
    }

    static List<GrantedAuthority> authoritiesOf(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        if (user.getRole() == UserRole.TEAM_MEMBER) {
            for (TeamModule module : user.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(module.authority()));
            }
        }
        return authorities;
    }
}
