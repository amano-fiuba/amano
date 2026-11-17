package com.amano.backend.income;

/**
 * Closed income category catalog (specs/032-income-management, FR-003). The
 * persisted value is the stable English code; Spanish labels live in the
 * frontend ({@code expenseCatalog.js}). A CHECK in {@code V38__create_incomes.sql}
 * mirrors this list as the database backstop.
 */
public enum IncomeCategory {
    SALARY,
    FREELANCE,
    SALE,
    REFUND,
    OTHER
}
