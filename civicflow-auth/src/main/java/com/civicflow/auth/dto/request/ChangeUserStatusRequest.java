package com.civicflow.auth.dto.request;

import com.civicflow.auth.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeUserStatusRequest(
        @NotNull UserStatus status, @NotNull @PositiveOrZero Integer version) {}
