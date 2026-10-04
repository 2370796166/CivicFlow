package com.civicflow.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CheckInClaimRequest(
        @NotBlank @Size(max = 4096) String token,
        @Positive long userId,
        @Positive long outletId,
        @NotBlank @Size(max = 36) String claimId) {}
