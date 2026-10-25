package com.growthprint.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_LOG_KEY = "correlationId";
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = getOrCreateCorrelationId(request);

        try {
            MDC.put(CORRELATION_ID_LOG_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_LOG_KEY);
        }
    }

    private String getOrCreateCorrelationId(HttpServletRequest request) {
        String incomingId = request.getHeader(CORRELATION_ID_HEADER);

        if (incomingId != null && isValidCorrelationId(incomingId)) {
            return incomingId;
        }

        return UUID.randomUUID().toString();
    }

    private boolean isValidCorrelationId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        if (id.length() > MAX_CORRELATION_ID_LENGTH) {
            return false;
        }

        // Reject control characters
        for (char c : id.toCharArray()) {
            if (Character.isISOControl(c)) {
                return false;
            }
        }

        return true;
    }
}
