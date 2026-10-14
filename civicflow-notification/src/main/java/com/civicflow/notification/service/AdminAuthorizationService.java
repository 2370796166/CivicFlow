package com.civicflow.notification.service;

import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.common.trace.TraceHeaders;
import com.civicflow.notification.client.AuthCurrentUserClient;
import com.civicflow.notification.error.NotificationErrorCode;
import feign.FeignException;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {
    private final AuthCurrentUserClient auth;

    public AdminAuthorizationService(AuthCurrentUserClient auth) {
        this.auth = auth;
    }

    public void validate(String token, String userId, String requestId) {
        com.civicflow.common.api.ApiResponse<AuthCurrentUserClient.User> response;
        try {
            response =
                    auth.currentUser(
                            "Bearer " + token, requestId, TraceHeaders.traceparent(requestId));
        } catch (FeignException exception) {
            if (exception.status() == 401 || exception.status() == 403)
                throw new BusinessException(NotificationErrorCode.FORBIDDEN);
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        if (response == null
                || !CommonErrorCode.SUCCESS.code().equals(response.code())
                || response.data() == null
                || response.data().roles() == null)
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        if (!userId.equals(response.data().id()) || !response.data().roles().contains("ADMIN"))
            throw new BusinessException(NotificationErrorCode.FORBIDDEN);
    }
}
