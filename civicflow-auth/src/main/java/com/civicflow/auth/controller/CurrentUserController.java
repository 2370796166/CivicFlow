package com.civicflow.auth.controller;

import com.civicflow.auth.config.RequestIdFilter;
import com.civicflow.auth.dto.response.CurrentUserResponse;
import com.civicflow.auth.service.AuthenticationService;
import com.civicflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
public class CurrentUserController {
    private final AuthenticationService authenticationService;

    public CurrentUserController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @GetMapping("/me")
    ApiResponse<CurrentUserResponse> me(
            JwtAuthenticationToken authentication, HttpServletRequest request) {
        Number tokenVersion = authentication.getToken().getClaim("tokenVersion");
        return ApiResponse.success(
                authenticationService.currentUser(
                        Long.parseLong(authentication.getName()), tokenVersion.intValue()),
                RequestIdFilter.current(request));
    }
}
