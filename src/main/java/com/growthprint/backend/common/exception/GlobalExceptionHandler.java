package com.growthprint.backend.common.exception;

import com.growthprint.backend.client.exception.ClientNotFoundException;
import com.growthprint.backend.common.dto.ApiErrorResponse;
import com.growthprint.backend.crm.lead.exception.CrmLeadAlreadyExistsException;
import com.growthprint.backend.crm.lead.exception.CrmLeadConversionException;
import com.growthprint.backend.crm.lead.exception.CrmLeadNotFoundException;
import com.growthprint.backend.crm.lead.exception.InquiryNotFoundException;
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

    private org.springframework.http.ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), fieldErrors);
        return org.springframework.http.ResponseEntity.status(status).body(body);
    }
}
