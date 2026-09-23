package com.civicflow.appointment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConfirmAppointmentRequest(@NotNull @Min(0) Integer version) {}
