package com.amano.backend.expense;

/**
 * State of a recurring expense series (009, FR-007): only {@code ACTIVE} series
 * generate occurrences; {@code FINISHED} is final (FR-009).
 */
public enum RecurrenceStatus {
    ACTIVE,
    PAUSED,
    FINISHED
}
