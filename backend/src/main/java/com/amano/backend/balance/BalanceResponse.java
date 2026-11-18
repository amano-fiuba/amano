package com.amano.backend.balance;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Personal balance of a period (specs/032-income-management, contracts/personal-incomes-api.md),
 * in the account's main currency, every amount a canonical two-decimal string.
 * {@code net = income - expenses}; scheduled (future) incomes are only informative
 * in {@link #projectedIncome}, which is {@code null} (omitted) when no quote exists
 * to convert them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BalanceResponse(
        String currency,
        String income,
        String projectedIncome,
        String expenses,
        String net) {
}
