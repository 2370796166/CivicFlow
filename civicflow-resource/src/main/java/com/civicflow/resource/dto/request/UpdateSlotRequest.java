package com.civicflow.resource.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateSlotRequest(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,19}") String outletId,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,19}") String itemId,
        @NotNull LocalDate serviceDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) @Max(1000000) int totalQuota,
        @NotNull Instant releaseAt,
        @NotNull Instant checkInStart,
        @NotNull Instant checkInEnd,
        @Min(0) int version) {}
