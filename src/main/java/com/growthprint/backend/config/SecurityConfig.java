package com.growthprint.backend.config;

import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.security.JwtAccessDeniedHandler;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import com.growthprint.backend.security.JwtAuthenticationEntryPoint;
import com.growthprint.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ROLE_ADMIN";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint, JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    /**
     * Single source of truth for CORS (previously duplicated in CorsConfig).
     * Origins come from app.cors.allowed-origins / CORS_ALLOWED_ORIGINS (comma-separated).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(parseAllowedOrigins(corsAllowedOrigins));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setExposedHeaders(List.of("X-Correlation-ID"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    /**
     * Parses a comma-separated origin list, trimming whitespace and dropping empty entries.
     * A wildcard "*" is rejected because credentials are allowed.
     */
    static List<String> parseAllowedOrigins(String rawOrigins) {
        if (rawOrigins == null) {
            return List.of();
        }
        List<String> origins = Arrays.stream(rawOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must list explicit origins; '*' is not allowed with credentials");
        }
        return origins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF is not needed: the API is stateless and authenticates via the Authorization header,
                // not cookies, so browsers cannot attach credentials cross-site automatically.
                .csrf(csrf -> csrf.disable())
                // Call the @Bean method directly (proxied -> same singleton). Injecting by type would be
                // ambiguous because Spring MVC's HandlerMappingIntrospector is also a CorsConfigurationSource.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        // JSON API responses never need to load or frame anything. Scoped to /api/** so the
                        // optional Swagger UI keeps working.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                                new StaticHeadersWriter("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin")))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/public/inquiries").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/logout").permitAll()
                        // Account recovery + invitations (rate limited per IP in RateLimitFilter)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/password/policy").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/password/reset/verify",
                                "/api/v1/auth/invitations/verify",
                                "/api/v1/auth/invitations/accept").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Authenticated endpoints
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        // Self-service account settings: always the caller's own account
                        .requestMatchers("/api/v1/account/**").authenticated()

                        // Client-specific endpoints - require CLIENT role with server-side ownership validation
                        .requestMatchers("/api/v1/support-requests/my/**").hasRole("CLIENT")
                        .requestMatchers("/api/v1/invoices/my").hasRole("CLIENT")
                        // Client payment reports (must precede the ADMIN-only /api/v1/invoices/** rule)
                        .requestMatchers("/api/v1/invoices/my/**").hasRole("CLIENT")
                        .requestMatchers("/api/v1/projects/my").hasRole("CLIENT")
                        .requestMatchers("/api/v1/proposals/my/**").hasRole("CLIENT")

                        // Internal modules: ADMIN, or a TEAM_MEMBER the admin granted that module to.
                        // Authorities come from the database on every request (AccountSessionService),
                        // so revoking a permission takes effect immediately.
                        .requestMatchers("/api/v1/clients/**").hasAnyAuthority(ADMIN, TeamModule.CLIENTS.authority())
                        .requestMatchers("/api/v1/leads/**").hasAnyAuthority(ADMIN, TeamModule.CRM.authority())
                        .requestMatchers("/api/v1/crm-leads/**").hasAnyAuthority(ADMIN, TeamModule.CRM.authority())
                        // Finance stays ADMIN-only (not grantable to team members)
                        .requestMatchers("/api/v1/invoices/**").hasRole("ADMIN")
                        // AI triage spends provider credits: ADMIN only (must precede support-requests/**)
                        .requestMatchers("/api/v1/support-requests/*/ai/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/support-requests/**").hasAnyAuthority(ADMIN, TeamModule.SUPPORT.authority())
                        .requestMatchers("/api/v1/projects/**").hasAnyAuthority(ADMIN, TeamModule.PROJECTS.authority())
                        .requestMatchers("/api/v1/outreach/**").hasAnyAuthority(ADMIN, TeamModule.OUTREACH.authority())
                        .requestMatchers("/api/v1/proposals/**").hasAnyAuthority(ADMIN, TeamModule.PROPOSALS.authority())
                        // AI provider settings (keys, models, limits) — admin only
                        .requestMatchers("/api/v1/admin/ai/**").hasRole("ADMIN")
                        // User, staff, permission management and security overview — admin only
                        .requestMatchers("/api/v1/admin/users/**", "/api/v1/admin/security/**").hasRole("ADMIN")

                        // Development-only seed endpoint - requires ADMIN role and dev profile
                        // Note: Only active when spring.profiles.active includes "dev" or "development"
                        .requestMatchers("/api/v1/dev/seed").hasRole("ADMIN")

                        // Deny all other requests
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JwtAuthenticationFilter is a @Component, so Spring Boot would ALSO register it as a plain servlet filter
     * outside Spring Security. It must only run inside the security chain (added above via addFilterBefore):
     * if the standalone copy runs before the chain, it marks the request "already filtered", the chain's
     * SecurityContextHolderFilter resets the (stateless) context, the in-chain copy is skipped and every
     * authenticated request becomes a 401. Production only worked because of servlet filter ordering; the
     * role-matrix test exposed it. Disabling the standalone registration is Spring's recommended setup.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter>
            jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
