package com.civicflow.appointment.event;

public final class AppointmentMessaging {
    public static final String APPOINTMENT_EXCHANGE = "cf.appointment.x";
    public static final String DEAD_LETTER_EXCHANGE = "cf.dlx";
    public static final String RESERVATION_REQUESTED_ROUTING_KEY =
            "appointment.reservation.requested.v1";
    public static final String RESERVATION_CREATE_QUEUE = "cf.appointment.reservation.create.q";
    public static final String RESERVATION_CREATE_DLQ = "cf.appointment.reservation.create.q.dlq";
    public static final String RESERVATION_CREATE_DLQ_ROUTING_KEY =
            "cf.appointment.reservation.create.q.dead";
    public static final String RESERVATION_CREATE_CONSUMER = "appointment-reservation-create-v1";
    public static final String TIMEOUT_EXCHANGE = "cf.timeout.x";
    public static final String TIMEOUT_SCHEDULE_ROUTING_KEY =
            "appointment.confirm.timeout.schedule.v1";
    public static final String TIMEOUT_CHECK_ROUTING_KEY = "appointment.confirm.timeout.check.v1";
    public static final String TIMEOUT_DELAY_QUEUE = "cf.appointment.confirm.delay.5m.q";
    public static final String TIMEOUT_CHECK_QUEUE = "cf.appointment.confirm.timeout.q";
    public static final String TIMEOUT_CHECK_DLQ = "cf.appointment.confirm.timeout.q.dlq";
    public static final String TIMEOUT_DLQ_ROUTING_KEY = "cf.appointment.confirm.timeout.q.dead";
    public static final String TIMEOUT_EVENT_TYPE = "appointment.confirm.timeout.requested";
    public static final String CHECKIN_CLAIMED_EVENT_TYPE = "appointment.check-in.claimed";
    public static final String CHECKIN_CLAIMED_ROUTING_KEY = "appointment.check-in.claimed.v1";
    public static final String CHECKIN_CLAIMED_QUEUE = "cf.queue.checkin-claim.q";

    private AppointmentMessaging() {}
}
