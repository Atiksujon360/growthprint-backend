package com.growthprint.backend.config;

import com.growthprint.backend.common.web.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Per-client-IP rate limiting for unauthenticated, abuse-prone endpoints:
 * <ul>
 *     <li>POST /api/v1/public/inquiries (spam protection)</li>
 *     <li>POST /api/v1/auth/login (password brute-force protection)</li>
 *     <li>account recovery: forgot/reset password and invitation verify/accept (token guessing, email
 *     flooding)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String PUBLIC_INQUIRIES_PATH = "/api/v1/public/inquiries";
    static final String LOGIN_PATH = "/api/v1/auth/login";
    static final Set<String> ACCOUNT_RECOVERY_PATHS = Set.of(
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/password/reset/verify",
            "/api/v1/auth/invitations/verify",
            "/api/v1/auth/invitations/accept");
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Rate limit exceeded. Please try again later.";

    private final InMemoryRateLimiter rateLimiter;
    private final RateLimitConfig rateLimitConfig;
    private final LoginRateLimitConfig loginRateLimitConfig;
    private final AccountRecoveryRateLimitConfig accountRecoveryRateLimitConfig;

    public RateLimitFilter(InMemoryRateLimiter rateLimiter, RateLimitConfig rateLimitConfig,
                           LoginRateLimitConfig loginRateLimitConfig,
                           AccountRecoveryRateLimitConfig accountRecoveryRateLimitConfig) {
        this.rateLimiter = rateLimiter;
        this.rateLimitConfig = rateLimitConfig;
        this.loginRateLimitConfig = loginRateLimitConfig;
        this.accountRecoveryRateLimitConfig = accountRecoveryRateLimitConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String path = request.getRequestURI();

            if (PUBLIC_INQUIRIES_PATH.equals(path)
                    && !allowed(request, "inquiry", rateLimitConfig.getMaxRequests(), rateLimitConfig.getWindow())) {
                sendRateLimitExceededResponse(response, path, rateLimitConfig.getWindow());
                return;
            }

            if (LOGIN_PATH.equals(path)
                    && !allowed(request, "login", loginRateLimitConfig.getMaxRequests(),
                    loginRateLimitConfig.getWindow())) {
                sendRateLimitExceededResponse(response, path, loginRateLimitConfig.getWindow());
                return;
            }

            // One shared bucket for all recovery endpoints so they cannot be combined to multiply attempts.
            if (ACCOUNT_RECOVERY_PATHS.contains(path)
                    && !allowed(request, "account-recovery", accountRecoveryRateLimitConfig.getMaxRequests(),
                    accountRecoveryRateLimitConfig.getWindow())) {
                sendRateLimitExceededResponse(response, path, accountRecoveryRateLimitConfig.getWindow());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean allowed(HttpServletRequest request, String bucketPrefix, int maxRequests, Duration window) {
        // Separate buckets per endpoint so inquiry traffic never blocks logins and vice versa.
        String key = bucketPrefix + ":" + rateLimiter.getClientIdentifier(request);
        return rateLimiter.tryAcquire(key, maxRequests, window);
    }

    private void sendRateLimitExceededResponse(HttpServletResponse response, String path, Duration window)
            throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, window.toSeconds())));
        ApiErrorResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_EXCEEDED_MESSAGE, path);
    }
}
