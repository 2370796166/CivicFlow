package com.civicflow.resource.dto.response;

import java.time.LocalDate;

public record SlotBatchFailureResponse(LocalDate serviceDate, String code, String message) {}
