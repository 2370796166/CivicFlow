package com.civicflow.auth.service;

import com.civicflow.auth.dto.request.LoginRequest;
import com.civicflow.auth.dto.response.CurrentUserResponse;
import com.civicflow.auth.dto.response.TokenPairResponse;

public interface AuthenticationService {
    TokenPairResponse login(LoginRequest request);

    TokenPairResponse refresh(String refreshToken, String requestId);

    void logout(long authenticatedUserId, String refreshToken);

    CurrentUserResponse currentUser(long authenticatedUserId, int tokenVersion);
}
