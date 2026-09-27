package com.civicflow.appointment.dto.response;

import java.time.Instant;

public record CheckInTokenResponse(String token, Instant expiresAt) {}
