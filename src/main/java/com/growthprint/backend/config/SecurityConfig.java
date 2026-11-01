package com.growthprint.backend.config;

import com.growthprint.backend.security.JwtAccessDeniedHandler;
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
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/public/inquiries").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Authenticated endpoints
                        .requestMatchers("/api/v1/auth/me").authenticated()

                        // Client-specific endpoints - require CLIENT role with server-side ownership validation
                        .requestMatchers("/api/v1/support-requests/my/**").hasRole("CLIENT")
                        .requestMatchers("/api/v1/invoices/my").hasRole("CLIENT")
                        // Client payment reports (must precede the ADMIN-only /api/v1/invoices/** rule)
                        .requestMatchers("/api/v1/invoices/my/**").hasRole("CLIENT")
                        .requestMatchers("/api/v1/projects/my").hasRole("CLIENT")
                        .requestMatchers("/api/v1/proposals/my/**").hasRole("CLIENT")

                        // Admin-only endpoints
                        .requestMatchers("/api/v1/clients/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/leads/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/crm-leads/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/invoices/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/support-requests/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/projects/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/outreach/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/proposals/**").hasRole("ADMIN")

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
