package com.civicflow.appointment.service;

import com.civicflow.appointment.dto.response.AppointmentResponse;

public interface AppointmentStateService {
    AppointmentResponse confirm(
            long userId, long appointmentId, int version, String key, String traceId);

    AppointmentResponse cancel(
            long userId,
            long appointmentId,
            int version,
            String reason,
            String key,
            String traceId);

    void expire(long appointmentId, String traceId);
}
