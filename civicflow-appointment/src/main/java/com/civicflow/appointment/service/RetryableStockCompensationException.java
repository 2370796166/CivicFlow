package com.civicflow.appointment.service;

public class RetryableStockCompensationException extends RuntimeException {
    public RetryableStockCompensationException(String message, Throwable cause) {
        super(message, cause);
    }
}
