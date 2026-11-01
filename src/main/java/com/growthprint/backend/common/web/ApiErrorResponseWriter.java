package com.growthprint.backend.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Writes {@link ApiErrorResponse} JSON directly to the servlet response.
 * <p>
 * Used by servlet filters and Spring Security handlers, which run outside Spring MVC and
 * therefore cannot rely on {@code GlobalExceptionHandler}. Uses the same date format as
 * {@code JacksonConfig} (ISO-8601 strings) so every error body looks identical.
 */
public final class ApiErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ApiErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(status, message, path, Map.of());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
