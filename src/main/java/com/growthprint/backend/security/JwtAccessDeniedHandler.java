package com.growthprint.backend.security;

import com.growthprint.backend.common.web.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a JSON 403 when an authenticated user lacks the required role.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI());
    }
}
