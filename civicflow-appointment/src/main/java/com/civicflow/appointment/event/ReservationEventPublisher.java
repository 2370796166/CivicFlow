package com.civicflow.appointment.event;

public interface ReservationEventPublisher {
    PublishResult publish(ReservationRequestedEvent event);

    enum Outcome {
        ACKNOWLEDGED,
        FAILED,
        UNKNOWN
    }

    record PublishResult(Outcome outcome, int attempts, String errorCode) {}
}
