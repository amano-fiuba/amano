package com.amano.backend.expense;

/**
 * Raised when the requested series does not exist, belongs to another user or is
 * not a recurring series at all (one-time, occurrence or legacy 007 row). One
 * neutral 404 for every case, so the existence of other users' expenses never
 * leaks (009, FR-011).
 */
public class RecurringExpenseNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No se encontró el gasto recurrente";

    public RecurringExpenseNotFoundException() {
        super(MESSAGE);
    }
}
