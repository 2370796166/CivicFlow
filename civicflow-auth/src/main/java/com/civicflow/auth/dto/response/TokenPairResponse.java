package com.civicflow.auth.dto.response;

public record TokenPairResponse(
        String accessToken, long expiresIn, String refreshToken, CurrentUserResponse user) {}
