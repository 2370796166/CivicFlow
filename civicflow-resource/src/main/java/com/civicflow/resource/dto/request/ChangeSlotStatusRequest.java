package com.civicflow.resource.dto.request;

import com.civicflow.resource.enums.SlotStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeSlotStatusRequest(@NotNull SlotStatus status, @Min(0) int version) {}
