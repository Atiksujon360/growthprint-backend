package com.growthprint.backend.testsupport;

import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.security.AccountSessionService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Stubs for {@link AccountSessionService} in HTTP-level security tests without a database: every signature-valid
 * token is treated as belonging to an active account with the token's role. Team members get the modules
 * supplied by {@code teamModules}.
 */
public final class SessionStubs {

    private SessionStubs() {
    }

    public static void acceptAllTokens(AccountSessionService sessions) {
        acceptAllTokens(sessions, Set::of);
    }

    public static void acceptAllTokens(AccountSessionService sessions, Supplier<Set<TeamModule>> teamModules) {
        when(sessions.authoritiesFor(any(UUID.class), anyString(), anyInt())).thenAnswer(invocation -> {
            String role = invocation.getArgument(1);
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            if ("TEAM_MEMBER".equals(role)) {
                teamModules.get().forEach(module -> authorities.add(new SimpleGrantedAuthority(module.authority())));
            }
            return Optional.of(authorities);
        });
    }
}
