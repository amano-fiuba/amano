package com.amano.backend.expense;

/**
 * Raised when the requested personal expense does not exist or belongs to another
 * user. One neutral 404 for both cases, so the existence of other users' expenses
 * never leaks (031, FR-016).
 */
public class ExpenseNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No encontramos ese gasto";

    public ExpenseNotFoundException() {
        super(MESSAGE);
    }
}
