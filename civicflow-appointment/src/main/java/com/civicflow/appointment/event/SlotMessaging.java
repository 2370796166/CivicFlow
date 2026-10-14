package com.civicflow.appointment.event;

public final class SlotMessaging {
    public static final String EXCHANGE = "cf.resource.x";
    public static final String RETRY_EXCHANGE = "cf.resource.retry.x";
    public static final String ROUTING = "resource.slot.changed.v1";
    public static final String RETRY_ROUTING = "resource.slot.changed.retry";
    public static final String DEAD_ROUTING = "resource.slot.changed.dead";
    public static final String QUEUE = "cf.appointment.slot-config.q";
    public static final String RETRY_QUEUE = "cf.appointment.slot-config.retry.q";
    public static final String DEAD_QUEUE = "cf.appointment.slot-config.dlq";

    private SlotMessaging() {}
}
