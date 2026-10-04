package com.civicflow.appointment.convert;

import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.entity.AppointmentOrderEntity;

public final class AppointmentConverter {
    private AppointmentConverter() {}

    public static AppointmentResponse toResponse(AppointmentOrderEntity entity) {
        return new AppointmentResponse(
                entity.getId().toString(),
                entity.getReservationId(),
                entity.getSlotId().toString(),
                entity.getOutletId().toString(),
                entity.getItemId().toString(),
                entity.getOutletNameSnapshot(),
                entity.getItemNameSnapshot(),
                entity.getServiceDate(),
                entity.getSlotStartTime(),
                entity.getSlotEndTime(),
                entity.getStatus().name(),
                entity.getConfirmDeadline(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
