package com.civicflow.appointment.service;

import com.civicflow.appointment.dto.response.CheckInClaimResponse;
import com.civicflow.appointment.dto.response.CheckInTokenResponse;

public interface CheckInTokenService {
    CheckInTokenResponse issue(long userId, long appointmentId, long outletId);

    CheckInClaimResponse claim(
            String token, long userId, long outletId, String claimId, String traceId);

    void link(long appointmentId, String claimId, long ticketId);
}
