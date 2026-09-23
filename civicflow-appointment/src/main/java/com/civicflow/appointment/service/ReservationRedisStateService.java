package com.civicflow.appointment.service;

import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;

public interface ReservationRedisStateService {
    boolean markPublished(AppointmentReservationRequestEntity request);

    boolean markPersisted(AppointmentReservationRequestEntity request, long appointmentId);
}
