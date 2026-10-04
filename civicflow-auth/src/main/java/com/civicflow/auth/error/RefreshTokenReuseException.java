package com.civicflow.auth.error;

import com.civicflow.common.exception.BusinessException;

public class RefreshTokenReuseException extends BusinessException {
    public RefreshTokenReuseException() {
        super(AuthErrorCode.REFRESH_REUSED);
    }
}
