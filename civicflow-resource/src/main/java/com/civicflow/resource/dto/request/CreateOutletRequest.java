package com.civicflow.resource.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateOutletRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,63}") String code,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 256) String address,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @Size(min = 6, max = 32) String contactPhone) {}
