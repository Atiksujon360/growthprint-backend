package com.growthprint.backend.common.exception;

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
import com.growthprint.backend.support.exception.InvalidSupportRequestStatusTransitionException;
import com.growthprint.backend.support.exception.SupportRequestNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    private org.springframework.http.ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), fieldErrors);
        return org.springframework.http.ResponseEntity.status(status).body(body);
    }
}
