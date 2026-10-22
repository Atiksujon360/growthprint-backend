package com.growthprint.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private CorrelationIdFilter correlationIdFilter;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();
    }

    @Test
    void doFilterInternal_NoIncomingHeader_GeneratesNewId() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ValidIncomingHeader_UsesExistingId() throws Exception {
        String existingId = "valid-correlation-id-123";
        when(request.getHeader("X-Correlation-ID")).thenReturn(existingId);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Correlation-ID", existingId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidTooLongHeader_GeneratesNewId() throws Exception {
        String tooLongId = "a".repeat(100);
        when(request.getHeader("X-Correlation-ID")).thenReturn(tooLongId);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidControlCharacters_GeneratesNewId() throws Exception {
        String invalidId = "invalid\u0000id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(invalidId);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }
}
