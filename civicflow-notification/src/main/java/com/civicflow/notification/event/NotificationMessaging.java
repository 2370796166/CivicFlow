package com.civicflow.notification.event;

public final class NotificationMessaging {
    public static final String APPOINTMENT_EXCHANGE = "cf.appointment.x";
    public static final String QUEUE_EXCHANGE = "cf.queue.x";
    public static final String DEAD_EXCHANGE = "cf.dlx";
    public static final String IN_APP_QUEUE = "cf.notification.in-app.q";
    public static final String IN_APP_DEAD = "cf.notification.in-app.q.dead";
    public static final String APPOINTMENT_PATTERN = "appointment.notification.*.v1";
    public static final String QUEUE_PATTERN = "queue.notification.*.v1";

    private NotificationMessaging() {}
}
