package com.growthprint.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: these handlers used a bare ObjectMapper that could not serialize the Instant
 * timestamp, so 401/403 responses failed to produce a JSON body.
 */
class SecurityErrorHandlersTest {

    @Test
    void entryPointWritesJson401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationEntryPoint().commence(
                new MockHttpServletRequest("GET", "/api/v1/clients"), response, new BadCredentialsException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("Authentication required")
                .contains("/api/v1/clients")
                .containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T");
    }

    @Test
    void accessDeniedHandlerWritesJson403() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAccessDeniedHandler().handle(
                new MockHttpServletRequest("GET", "/api/v1/clients"), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"status\":403").contains("Access denied");
    }
}
