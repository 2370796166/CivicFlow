package com.civicflow.appointment.service;

import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.event.ReservationRequestedEvent;

public interface AppointmentCreationService {
    CreationResult create(ReservationRequestedEvent event, byte[] payloadHash);

    AppointmentReservationRequestEntity findRequest(String reservationId);

    Long findAppointmentId(String reservationId);

    record CreationResult(
            AppointmentReservationRequestEntity request, long appointmentId, boolean idempotent) {}
}
