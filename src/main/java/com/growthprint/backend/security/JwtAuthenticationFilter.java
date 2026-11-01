package com.growthprint.backend.security;

import com.growthprint.backend.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> KNOWN_ROLES = Arrays.stream(UserRole.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();

            // Parse + verify the token once; invalid tokens simply leave the request anonymous,
            // and the authorization rules in SecurityConfig then return 401/403.
            jwtTokenProvider.parseValidClaims(token)
                    .flatMap(JwtAuthenticationFilter::toAuthentication)
                    .ifPresent(authentication -> {
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }

    private static Optional<UsernamePasswordAuthenticationToken> toAuthentication(Claims claims) {
        String subject = claims.getSubject();
        String role = claims.get("role", String.class);
        if (subject == null || role == null || !KNOWN_ROLES.contains(role)) {
            return Optional.empty();
        }
        try {
            String userId = UUID.fromString(subject).toString();
            return Optional.of(new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
