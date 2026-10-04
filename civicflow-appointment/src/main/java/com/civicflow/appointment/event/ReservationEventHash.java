package com.civicflow.appointment.event;

import com.civicflow.appointment.support.Digests;

public final class ReservationEventHash {
    private ReservationEventHash() {}

    public static byte[] compute(ReservationRequestedEvent event) {
        ReservationRequestedEvent.Payload payload = event.payload();
        String canonical =
                String.join(
                        "\n",
                        Integer.toString(event.schemaVersion()),
                        event.eventId(),
                        event.eventType(),
                        Integer.toString(event.eventVersion()),
                        event.occurredAt().toString(),
                        event.producer(),
                        event.traceId(),
                        event.correlationId(),
                        event.causationId(),
                        payload.reservationId(),
                        payload.userId(),
                        payload.slotId(),
                        payload.outletId(),
                        payload.itemId(),
                        payload.outletName(),
                        payload.itemName(),
                        payload.serviceDate().toString(),
                        payload.slotStartTime().toString(),
                        payload.slotEndTime().toString(),
                        Integer.toString(payload.totalQuota()),
                        payload.releaseAt().toString(),
                        payload.closeAt().toString(),
                        payload.slotStatus(),
                        Long.toString(payload.slotConfigVersion()),
                        payload.reservedAt().toString(),
                        payload.reservationExpiresAt().toString());
        return Digests.sha256(canonical);
    }
}
