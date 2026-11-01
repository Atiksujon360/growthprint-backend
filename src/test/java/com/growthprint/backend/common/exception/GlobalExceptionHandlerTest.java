package com.growthprint.backend.common.exception;

import com.growthprint.backend.auth.exception.AuthenticationFailedException;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");

    @Test
    void failedLoginIs401WithGenericMessage() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAuthenticationFailed(new AuthenticationFailedException("anything"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().message()).isEqualTo("Invalid credentials");
    }

    @Test
    void methodSecurityDenialIs403NotServerError() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void illegalArgumentIs400AndIllegalStateIs409() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("bad"), request)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(handler.handleIllegalState(new IllegalStateException("Only draft proposals can be updated"), request)
                .getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void unexpectedExceptionIsSanitized500() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("SQL: select * from secret_table"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("secret_table");
    }

    @Test
    void standardSpringClientErrorsKeepTheirStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(new HttpRequestMethodNotSupportedException("DELETE"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
    }
}
