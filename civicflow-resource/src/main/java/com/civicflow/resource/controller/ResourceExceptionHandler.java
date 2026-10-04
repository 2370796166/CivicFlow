package com.civicflow.resource.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.common.exception.ErrorDetail;
import com.civicflow.resource.config.RequestIdFilter;
import com.civicflow.resource.error.ResourceSecurityErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ResourceExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException exception, HttpServletRequest request) {
        return ResponseEntity.status(statusFor(exception.errorCode().code()))
                .body(
                        ApiResponse.failure(
                                exception.errorCode(), null, RequestIdFilter.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<List<ErrorDetail>>> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ErrorDetail> details =
                exception.getBindingResult().getAllErrors().stream()
                        .map(
                                error ->
                                        new ErrorDetail(
                                                error instanceof FieldError field
                                                        ? field.getField()
                                                        : error.getObjectName(),
                                                error.getDefaultMessage()))
                        .toList();
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.VALIDATION,
                                details,
                                RequestIdFilter.current(request)));
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ApiResponse<Void>> handleConstraint(
            RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.VALIDATION,
                                null,
                                RequestIdFilter.current(request)));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        CommonErrorCode code =
                "Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())
                        ? CommonErrorCode.IDEMPOTENCY_REQUIRED
                        : CommonErrorCode.VALIDATION;
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(code, null, RequestIdFilter.current(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.VALIDATION,
                                null,
                                RequestIdFilter.current(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        ApiResponse.failure(
                                ResourceSecurityErrorCode.FORBIDDEN,
                                null,
                                RequestIdFilter.current(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        LOGGER.error(
                "Unhandled resource exception requestId={} type={}",
                requestId,
                exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, null, requestId));
    }

    private static HttpStatus statusFor(String code) {
        if (code.contains("_404_")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.contains("_409_")) {
            return HttpStatus.CONFLICT;
        }
        if (code.contains("_503_")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
