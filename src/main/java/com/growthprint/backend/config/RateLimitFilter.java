package com.growthprint.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Rate limit exceeded. Please try again later.";

    private final InMemoryRateLimiter rateLimiter;
    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(InMemoryRateLimiter rateLimiter, RateLimitConfig rateLimitConfig) {
        this.rateLimiter = rateLimiter;
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate limit public inquiry POST requests
        if (isRateLimitedRequest(request)) {
            String clientIdentifier = rateLimiter.getClientIdentifier(request);
            boolean allowed = rateLimiter.tryAcquire(
                    clientIdentifier,
                    rateLimitConfig.getMaxRequests(),
                    rateLimitConfig.getWindow()
            );

            if (!allowed) {
                sendRateLimitExceededResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitedRequest(HttpServletRequest request) {
        return "/api/v1/public/inquiries".equals(request.getRequestURI())
                && "POST".equalsIgnoreCase(request.getMethod());
    }

    private void sendRateLimitExceededResponse(HttpServletResponse response) throws IOException {
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                RATE_LIMIT_EXCEEDED_MESSAGE,
                "/api/v1/public/inquiries",
                java.util.Map.of()
        );

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
