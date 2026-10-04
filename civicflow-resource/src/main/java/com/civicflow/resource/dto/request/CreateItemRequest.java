package com.civicflow.resource.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateItemRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,63}") String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1000) String description,
        @Min(1) @Max(1440) int defaultDurationMinutes) {}
