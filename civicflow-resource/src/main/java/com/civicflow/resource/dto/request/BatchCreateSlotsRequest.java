package com.civicflow.resource.dto.request;

import com.civicflow.resource.enums.SlotStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;

public record BatchCreateSlotsRequest(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,19}") String outletId,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,19}") String itemId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) @Max(1000000) int totalQuota,
        @Min(0) @Max(365) int releaseDaysBefore,
        @NotNull LocalTime releaseTime,
        @NotNull LocalTime checkInStart,
        @NotNull LocalTime checkInEnd,
        @NotNull SlotStatus status) {}
