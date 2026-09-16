package com.civicflow.auth.controller;

import com.civicflow.auth.config.RequestIdFilter;
import com.civicflow.auth.dto.request.LoginRequest;
import com.civicflow.auth.dto.request.LogoutRequest;
import com.civicflow.auth.dto.request.RefreshRequest;
import com.civicflow.auth.dto.response.TokenPairResponse;
import com.civicflow.auth.service.AuthenticationService;
import com.civicflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    ApiResponse<TokenPairResponse> login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return ApiResponse.success(
                authenticationService.login(body), RequestIdFilter.current(request));
    }

    @PostMapping("/refresh")
    ApiResponse<TokenPairResponse> refresh(
            @Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        return ApiResponse.success(
                authenticationService.refresh(
                        body.refreshToken(), RequestIdFilter.current(request)),
                RequestIdFilter.current(request));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody LogoutRequest body,
            HttpServletRequest request) {
        authenticationService.logout(Long.parseLong(authentication.getName()), body.refreshToken());
        return ApiResponse.success(null, RequestIdFilter.current(request));
    }
}
