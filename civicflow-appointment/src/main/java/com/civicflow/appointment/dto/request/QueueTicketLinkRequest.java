package com.civicflow.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record QueueTicketLinkRequest(
        @NotBlank String claimId, @Positive long ticketId, @NotBlank String eventId) {}
