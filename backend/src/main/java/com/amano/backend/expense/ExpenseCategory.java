package com.amano.backend.expense;

/**
 * Closed expense category catalog (FR-010/FR-011, data-model.md). The persisted
 * value is the stable English code; Spanish labels live only in the frontend
 * ({@code expenseCatalog.js}). A CHECK in {@code V4__create_expenses.sql} mirrors
 * this list as the database backstop.
 */
public enum ExpenseCategory {
    FOOD,
    TRANSPORT,
    HOUSING,
    SERVICES,
    HEALTH,
    EDUCATION,
    LEISURE,
    SHOPPING,
    PETS,
    OTHER
}
