package com.amano.backend.income;

/**
 * Raised when the requested personal income does not exist or belongs to another
 * user. One neutral 404 for both cases, so the existence of other users' incomes
 * never leaks (specs/033-income-management, FR-007).
 */
public class IncomeNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No encontramos ese ingreso";

    public IncomeNotFoundException() {
        super(MESSAGE);
    }
}
