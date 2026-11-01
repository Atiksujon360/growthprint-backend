package com.growthprint.backend.testsupport;

import com.growthprint.backend.common.exception.GlobalExceptionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

/**
 * Black-box HTTP tests without a database or Spring context: real controller + real service
 * (repositories mocked) + the real {@link GlobalExceptionHandler} and Bean Validation.
 * Authorization rules are covered separately by the role-matrix test.
 */
public final class StandaloneMvc {

    private StandaloneMvc() {
    }

    public static MockMvc of(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    /** Authenticates the current thread the same way JwtAuthenticationFilter does (principal = user id). */
    public static void authenticateAs(UUID userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
