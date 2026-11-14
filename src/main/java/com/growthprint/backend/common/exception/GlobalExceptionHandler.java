package com.growthprint.backend.common.exception;

import com.growthprint.backend.auth.exception.AuthenticationFailedException;
import com.growthprint.backend.auth.exception.CurrentPasswordMismatchException;
import com.growthprint.backend.auth.exception.InvalidAccountTokenException;
import com.growthprint.backend.auth.exception.WeakPasswordException;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.client.exception.ClientNotFoundException;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import com.growthprint.backend.crm.lead.exception.CrmLeadAlreadyExistsException;
import com.growthprint.backend.crm.lead.exception.CrmLeadConversionException;
import com.growthprint.backend.crm.lead.exception.CrmLeadNotFoundException;
import com.growthprint.backend.crm.lead.exception.InquiryNotFoundException;
import com.growthprint.backend.invoice.exception.InvoiceAlreadyExistsException;
import com.growthprint.backend.invoice.exception.InvalidInvoiceStatusTransitionException;
import com.growthprint.backend.invoice.exception.InvoiceNotFoundException;
import com.growthprint.backend.outreach.exception.InvalidOutreachStatusTransitionException;
import com.growthprint.backend.outreach.exception.OutreachDraftNotFoundException;
import com.growthprint.backend.project.exception.ProjectAlreadyExistsException;
import com.growthprint.backend.project.exception.ProjectNotFoundException;
import com.growthprint.backend.proposal.exception.ProposalExpiredException;
import com.growthprint.backend.proposal.exception.ProposalNotFoundException;
import com.growthprint.backend.support.exception.InvalidSupportRequestStatusTransitionException;
import com.growthprint.backend.support.exception.SupportRequestNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request body is missing or contains an invalid value",
                request, Map.of());
    }

    @ExceptionHandler(ClientNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleClientNotFound(
            ClientNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(CrmLeadNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleCrmLeadNotFound(
            CrmLeadNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InquiryNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleInquiryNotFound(
            InquiryNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(CrmLeadAlreadyExistsException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleCrmLeadAlreadyExists(
            CrmLeadAlreadyExistsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(CrmLeadConversionException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleCrmLeadConversion(
            CrmLeadConversionException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleProjectNotFound(
            ProjectNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ProjectAlreadyExistsException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleProjectAlreadyExists(
            ProjectAlreadyExistsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleInvoiceNotFound(
            InvoiceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvoiceAlreadyExistsException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleInvoiceAlreadyExists(
            InvoiceAlreadyExistsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidInvoiceStatusTransitionException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleInvalidStatusTransition(
            InvalidInvoiceStatusTransitionException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(OutreachDraftNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleOutreachDraftNotFound(
            OutreachDraftNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidOutreachStatusTransitionException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleInvalidOutreachStatusTransition(
            InvalidOutreachStatusTransitionException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(SupportRequestNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleSupportRequestNotFound(
            SupportRequestNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidSupportRequestStatusTransitionException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleInvalidSupportRequestStatusTransition(
            InvalidSupportRequestStatusTransitionException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ProposalNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleProposalNotFound(
            ProposalNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ProposalExpiredException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleProposalExpired(
            ProposalExpiredException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    /**
     * Unique / foreign-key / check constraint violations (e.g. duplicate CRM lead company+email, concurrent
     * proposal numbers). Previously a 500. The database message is logged but never returned: it contains
     * SQL and constraint names.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMostSpecificCause().getMessage());
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data", request, Map.of());
    }

    // ---------------------------------------------------------------------------------------------
    // Authentication / authorization
    // ---------------------------------------------------------------------------------------------

    @ExceptionHandler(AuthenticationFailedException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleAuthenticationFailed(
            AuthenticationFailedException exception, HttpServletRequest request) {
        // Always the same generic message: never reveal whether the email exists.
        return response(HttpStatus.UNAUTHORIZED, "Invalid credentials", request, Map.of());
    }

    @ExceptionHandler(WeakPasswordException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleWeakPassword(
            WeakPasswordException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Password does not meet the requirements", request,
                Map.of(exception.getField(), String.join(". ", exception.getViolations())));
    }

    @ExceptionHandler(InvalidAccountTokenException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleInvalidAccountToken(
            InvalidAccountTokenException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, InvalidAccountTokenException.MESSAGE, request, Map.of());
    }

    @ExceptionHandler(CurrentPasswordMismatchException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleCurrentPasswordMismatch(
            CurrentPasswordMismatchException exception, HttpServletRequest request) {
        // 400, not 401: a 401 would make the frontend treat it as an expired session.
        return response(HttpStatus.BAD_REQUEST, "Current password is incorrect", request,
                Map.of("currentPassword", "Current password is incorrect"));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleTooManyRequests(
            TooManyRequestsException exception, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(),
                request.getRequestURI(), Map.of());
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(Math.max(1, exception.getRetryAfter().toSeconds())))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(com.growthprint.backend.business.exception.BusinessExceptions.BusinessNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleBusinessNotFound(
            RuntimeException exception, HttpServletRequest request) {
        // Same answer for "does not exist" and "you are not a member" (no tenant enumeration).
        return response(HttpStatus.NOT_FOUND, "Business not found", request, Map.of());
    }

    @ExceptionHandler(com.growthprint.backend.business.exception.BusinessExceptions.BusinessAccessDeniedException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleBusinessAccessDenied(
            RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "User not found", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        // Thrown by @PreAuthorize inside controllers; must not fall into the generic 500 handler.
        return response(HttpStatus.FORBIDDEN, "Access denied", request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleSpringAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Authentication required", request, Map.of());
    }

    // ---------------------------------------------------------------------------------------------
    // Generic request / business-rule errors
    // ---------------------------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        // e.g. GET /api/v1/clients/not-a-uuid
        return response(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + exception.getName() + "'",
                request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        // Services use IllegalArgumentException for invalid input (e.g. invoice line item rules).
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException exception, HttpServletRequest request) {
        // Services use IllegalStateException for invalid state transitions (e.g. "Only draft proposals ...").
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    /**
     * Last-resort handler. Standard Spring MVC exceptions (404 no route, 405 wrong method, 415 media
     * type, ...) keep their own status; anything else becomes a sanitized 500 that is logged
     * server-side with the correlation id but never leaks internals to the client.
     */
    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return response(status, status.getReasonPhrase(), request, Map.of());
            }
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, Map.of());
    }

    private org.springframework.http.ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        // RFC 9457-compatible body, deliberately served as application/json (see ApiErrorResponse).
        ApiErrorResponse body = ApiErrorResponse.of(status, message, request.getRequestURI(), fieldErrors);
        return org.springframework.http.ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
