package com.civicflow.queue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
        @NotBlank @Size(max = 4096) String checkInToken, @Positive long outletId) {}
