package com.civicflow.appointment.service;

import com.civicflow.appointment.dto.response.AppointmentResponse;
import com.civicflow.appointment.dto.response.ReservationStatusResponse;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.common.api.PageResponse;
import java.time.LocalDate;

public interface AppointmentQueryService {
    ReservationStatusResponse getReservation(long userId, String reservationId);

    PageResponse<AppointmentResponse> listOwned(
            long userId, LocalDate serviceDate, AppointmentStatus status, int page, int size);

    AppointmentResponse getOwned(long userId, long appointmentId);
}
