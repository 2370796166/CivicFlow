package com.civicflow.common.exception;

import com.civicflow.common.api.GlobalErrorCode;
import java.util.Objects;

/** Base unchecked exception for stable business failures. */
public class BusinessException extends RuntimeException {

    private final GlobalErrorCode errorCode;

    public BusinessException(GlobalErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").message());
        this.errorCode = errorCode;
    }

    public BusinessException(GlobalErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").message(), cause);
        this.errorCode = errorCode;
    }

    public GlobalErrorCode errorCode() {
        return errorCode;
    }
}
