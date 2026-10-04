package com.civicflow.resource.dto.request;

import com.civicflow.resource.enums.ResourceStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeResourceStatusRequest(@NotNull ResourceStatus status, @Min(0) int version) {}
