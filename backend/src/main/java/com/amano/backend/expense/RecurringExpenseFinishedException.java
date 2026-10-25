package com.amano.backend.expense;

/**
 * Raised when a FINISHED series is asked to become ACTIVE or PAUSED: finishing is
 * final (009, FR-009). Answered as 409 with code {@code RECURRENCE_FINISHED}.
 */
public class RecurringExpenseFinishedException extends RuntimeException {

    public static final String MESSAGE = "El gasto recurrente ya está finalizado y no puede modificarse";
    public static final String CODE = "RECURRENCE_FINISHED";

    public RecurringExpenseFinishedException() {
        super(MESSAGE);
    }
}
