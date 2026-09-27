package com.civicflow.queue.client;

import com.civicflow.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        contextId = "appointment-queue-state",
        name = "civicflow-appointment",
        url = "${civicflow.queue.appointment-url:}",
        configuration = AppointmentClientConfig.class)
public interface AppointmentQueueStateClient {
    record Change(String ticketId, String status, String syncId) {}

    @PostMapping("/internal/v1/appointments/{id}/queue-state")
    ApiResponse<Void> change(@PathVariable("id") long appointmentId, @RequestBody Change body);
}
