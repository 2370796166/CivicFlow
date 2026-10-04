package com.civicflow.appointment.service;

import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.enums.StockReleaseReason;

public interface StockReleaseService {
    void release(AppointmentReservationRequestEntity request, StockReleaseReason reason);
}
