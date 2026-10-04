package com.civicflow.resource.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdjustSlotQuotaRequest(
        @Min(0) @Max(1000000) int totalQuota, @Min(1) long configVersion) {}
