package com.civicflow.resource.config;

import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityPrincipals {
    private SecurityPrincipals() {}

    public static long userId(JwtAuthenticationToken authentication) {
        try {
            return Long.parseLong(authentication.getToken().getSubject());
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION, exception);
        }
    }
}
