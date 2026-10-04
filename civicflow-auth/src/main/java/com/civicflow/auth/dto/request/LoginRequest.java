package com.civicflow.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 64) String loginName, @NotBlank @Size(max = 72) String password) {}
