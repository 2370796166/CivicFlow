package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.error.AppointmentSecurityErrorCode;
import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AppointmentExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException exception, HttpServletRequest request) {
        return ResponseEntity.status(statusFor(exception.errorCode().code()))
                .body(
                        ApiResponse.failure(
                                exception.errorCode(), null, RequestIdFilter.current(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(
            ConstraintViolationException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.VALIDATION,
                                null,
                                RequestIdFilter.current(request)));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> handleDependency(
            DataAccessException exception, HttpServletRequest request) {
        LOGGER.error(
                "Redis operation failed requestId={} type={}",
                RequestIdFilter.current(request),
                exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                                null,
                                RequestIdFilter.current(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleForbidden(
            AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        ApiResponse.failure(
                                AppointmentSecurityErrorCode.FORBIDDEN,
                                null,
                                RequestIdFilter.current(request)));
    }

    private static HttpStatus statusFor(String code) {
        if (code.contains("_404_")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.contains("_409_")) {
            return HttpStatus.CONFLICT;
        }
        if (code.contains("_410_")) {
            return HttpStatus.GONE;
        }
        if (code.contains("_422_")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if (code.contains("_503_")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
