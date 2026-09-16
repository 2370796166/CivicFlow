package com.civicflow.auth.dto.request;

import com.civicflow.auth.enums.RoleCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,63}") String username,
        @Pattern(regexp = "(?:\\+?86)?1[3-9]\\d{9}") String mobile,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 64) String displayName,
        @NotEmpty Set<@NotNull RoleCode> roles) {}
