package com.amano.backend.groupexpense.reminder;

/**
 * Lifecycle of a {@link Reminder} (025 data-model.md). Only
 * {@code SCHEDULED} rows that were never claimed can be rescheduled or
 * cancelled; {@code SENT}, {@code CANCELLED} and {@code FAILED} are final.
 */
public enum ReminderStatus {
    SCHEDULED,
    SENT,
    CANCELLED,
    FAILED
}
