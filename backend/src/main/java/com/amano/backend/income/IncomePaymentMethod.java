package com.amano.backend.income;

/**
 * Closed catalog of the ways an income is received (specs/033-income-management,
 * FR-003). A subset of the expense payment methods: cards do not apply to a
 * payment received. A CHECK in {@code V38__create_incomes.sql} mirrors this list.
 */
public enum IncomePaymentMethod {
    CASH,
    TRANSFER,
    VIRTUAL_WALLET,
    OTHER
}
