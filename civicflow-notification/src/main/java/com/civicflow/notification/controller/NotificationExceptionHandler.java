package com.civicflow.notification.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.notification.config.NotificationRequestIdFilter;
import com.civicflow.notification.error.NotificationErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class NotificationExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException exception) {
        String code = exception.errorCode().code();
        HttpStatus status =
                code.contains("_404_")
                        ? HttpStatus.NOT_FOUND
                        : code.contains("_409_") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(
                        ApiResponse.failure(
                                exception.errorCode(),
                                null,
                                NotificationRequestIdFilter.current()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> validation(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.VALIDATION,
                                null,
                                NotificationRequestIdFilter.current()));
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiResponse<Void>> invalid(Exception exception) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.VALIDATION,
                                null,
                                NotificationRequestIdFilter.current()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        ApiResponse.failure(
                                NotificationErrorCode.FORBIDDEN,
                                null,
                                NotificationRequestIdFilter.current()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> dependency(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                                null,
                                NotificationRequestIdFilter.current()));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiResponse<Void>> unexpected(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.INTERNAL_ERROR,
                                null,
                                NotificationRequestIdFilter.current()));
    }
}
