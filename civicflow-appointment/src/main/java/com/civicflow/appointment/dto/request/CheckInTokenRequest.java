package com.civicflow.appointment.dto.request;

import jakarta.validation.constraints.Positive;

public record CheckInTokenRequest(@Positive long outletId) {}
