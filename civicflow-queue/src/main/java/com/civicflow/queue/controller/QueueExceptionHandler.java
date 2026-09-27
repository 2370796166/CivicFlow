package com.civicflow.queue.controller;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.queue.config.RequestIdFilter;
import com.civicflow.queue.error.QueueErrorCode;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class QueueExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(
            BusinessException exception, HttpServletRequest request) {
        String code = exception.errorCode().code();
        HttpStatus status =
                code.contains("_404_")
                        ? HttpStatus.NOT_FOUND
                        : code.contains("_410_")
                                ? HttpStatus.GONE
                                : code.contains("_409_")
                                        ? HttpStatus.CONFLICT
                                        : code.contains("_422_")
                                                ? HttpStatus.UNPROCESSABLE_ENTITY
                                                : code.contains("_503_")
                                                        ? HttpStatus.SERVICE_UNAVAILABLE
                                                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(
                        ApiResponse.failure(
                                exception.errorCode(), null, RequestIdFilter.current(request)));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalid(HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.failure(
                                CommonErrorCode.VALIDATION,
                                null,
                                RequestIdFilter.current(request)));
    }

    @ExceptionHandler(FeignException.class)
    ResponseEntity<ApiResponse<Void>> upstream(
            FeignException exception, HttpServletRequest request) {
        HttpStatus status =
                exception.status() == 404
                        ? HttpStatus.NOT_FOUND
                        : exception.status() == 410
                                ? HttpStatus.GONE
                                : exception.status() == 409
                                        ? HttpStatus.CONFLICT
                                        : exception.status() == 422
                                                ? HttpStatus.UNPROCESSABLE_ENTITY
                                                : HttpStatus.SERVICE_UNAVAILABLE;
        var code =
                status == HttpStatus.NOT_FOUND
                        ? CommonErrorCode.NOT_FOUND
                        : status == HttpStatus.GONE
                                ? QueueErrorCode.QR_EXPIRED
                                : status == HttpStatus.CONFLICT
                                        ? QueueErrorCode.STATE_CONFLICT
                                        : status == HttpStatus.UNPROCESSABLE_ENTITY
                                                ? QueueErrorCode.CHECKIN_WINDOW
                                                : QueueErrorCode.CHECKIN_PENDING;
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(code, null, RequestIdFilter.current(request)));
    }
}
