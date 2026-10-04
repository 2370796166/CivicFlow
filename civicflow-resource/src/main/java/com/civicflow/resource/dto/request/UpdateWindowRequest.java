package com.civicflow.resource.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWindowRequest(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,19}") String outletId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,63}") String code,
        @NotBlank @Size(max = 128) String name,
        @Min(0) int version) {}
