package com.civicflow.appointment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(
        @NotBlank @Size(max = 256) String reason, @NotNull @Min(0) Integer version) {}
