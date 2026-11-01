package com.growthprint.backend.ai.web;

import com.growthprint.backend.ai.provider.AiException;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Maps {@link AiException} to the standard error body. The RFC 9457 {@code type} carries a stable code
 * ({@code urn:growthprint:ai:INVALID_CREDENTIALS} etc.) so the settings screen can show the right state;
 * the message is always the fixed, safe text for that code.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {

    public static final String TYPE_PREFIX = "urn:growthprint:ai:";

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiErrorResponse> handle(AiException exception, HttpServletRequest request) {
        var status = exception.code().httpStatus();
        ApiErrorResponse body = new ApiErrorResponse(TYPE_PREFIX + exception.code().name(), status.getReasonPhrase(),
                status.value(), exception.getMessage(), request.getRequestURI(), Instant.now(), status.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI(), Map.of());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
