package com.civicflow.queue.client;

import com.civicflow.common.api.ApiResponse;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "civicflow-appointment",
        url = "${civicflow.queue.appointment-url:}",
        configuration = AppointmentClientConfig.class)
public interface AppointmentCheckInClient {
    @PostMapping("/internal/v1/appointments/check-in/claim")
    ApiResponse<ClaimResponse> claim(@RequestBody ClaimRequest request);

    @PostMapping("/internal/v1/appointments/{id}/queue-ticket-link")
    ApiResponse<Void> link(
            @PathVariable("id") long appointmentId, @RequestBody LinkRequest request);

    record ClaimRequest(String token, long userId, long outletId, String claimId) {}

    record ClaimResponse(
            String claimId,
            String appointmentId,
            String userId,
            String outletId,
            String itemId,
            LocalDate serviceDate,
            Instant checkedInAt,
            String status) {}

    record LinkRequest(String claimId, long ticketId, String eventId) {}
}
