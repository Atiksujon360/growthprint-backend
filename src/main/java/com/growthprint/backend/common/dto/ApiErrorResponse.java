package com.growthprint.backend.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Error body for every API error.
 * <p>
 * RFC 9457 (Problem Details) compatible: carries the standard members {@code type}, {@code title},
 * {@code status}, {@code detail} and {@code instance}. It also keeps the pre-existing members
 * {@code timestamp}, {@code error}, {@code message}, {@code path} and {@code fieldErrors} (RFC 9457
 * "extension members"), because the frontend reads {@code message}.
 * <p>
 * Served as {@code application/json}, not {@code application/problem+json}: the frontend API client only
 * parses bodies whose Content-Type contains "application/json" and would otherwise lose the message.
 */
@Schema(description = "API error (RFC 9457-compatible problem details with GrowthPrint extension members)")
public record ApiErrorResponse(
        @Schema(description = "Problem type URI (RFC 9457); 'about:blank' = described by the HTTP status",
                example = "about:blank")
        String type,
        @Schema(description = "Short summary of the problem type (RFC 9457)", example = "Bad Request")
        String title,
        @Schema(description = "HTTP status code (RFC 9457)", example = "400")
        int status,
        @Schema(description = "Human-readable explanation of this occurrence (RFC 9457); same as message")
        String detail,
        @Schema(description = "Request path of this occurrence (RFC 9457); same as path",
                example = "/api/v1/invoices")
        String instance,
        @Schema(description = "When the error occurred (UTC)")
        Instant timestamp,
        @Schema(description = "HTTP reason phrase (legacy; same as title)", example = "Bad Request")
        String error,
        @Schema(description = "Human-readable message shown by the frontend (legacy; same as detail)")
        String message,
        @Schema(description = "Request path (legacy; same as instance)")
        String path,
        @Schema(description = "Field name -> validation message; empty when not a validation error")
        Map<String, String> fieldErrors
) {

    public static final String DEFAULT_TYPE = "about:blank";

    public static ApiErrorResponse of(HttpStatus status, String message, String path,
                                      Map<String, String> fieldErrors) {
        return new ApiErrorResponse(DEFAULT_TYPE, status.getReasonPhrase(), status.value(), message, path,
                Instant.now(), status.getReasonPhrase(), message, path,
                fieldErrors == null ? Map.of() : fieldErrors);
    }
}
