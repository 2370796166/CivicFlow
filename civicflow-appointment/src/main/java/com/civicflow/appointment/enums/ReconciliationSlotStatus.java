package com.civicflow.appointment.enums;

public enum ReconciliationSlotStatus {
    SCHEDULED,
    OPEN,
    SUSPENDED;

    public static boolean eligible(String value) {
        if (value == null) {
            return false;
        }
        try {
            valueOf(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
