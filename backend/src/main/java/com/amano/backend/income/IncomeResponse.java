package com.amano.backend.income;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public representation of a personal income (specs/033-income-management,
 * contracts/personal-incomes-api.md). The amount is a canonical two-decimal string.
 * {@link #projected} is derived from the date, never stored: an income dated after
 * today (UTC) is a scheduled one that does not count in the balance yet (FR-009).
 * {@link #conversions} is only attached where the activity needs the equivalent in
 * the preferred currencies (search and detail), so it is omitted from the JSON
 * otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncomeResponse(
        Long id,
        String amount,
        String currency,
        String date,
        String category,
        String paymentMethod,
        String description,
        boolean projected,
        OffsetDateTime createdAt,
        List<Conversion> conversions) {

    /** One preferred-currency equivalent of an income, canonical 2-decimal string. */
    public record Conversion(String currency, String amount) {
    }

    public static IncomeResponse from(Income income) {
        return from(income, null);
    }

    public static IncomeResponse from(Income income, List<Conversion> conversions) {
        return new IncomeResponse(
                income.getId(),
                income.getAmount().toPlainString(),
                income.getCurrency().name(),
                income.getIncomeDate().toString(),
                income.getCategory().name(),
                income.getPaymentMethod().name(),
                income.getDescription(),
                isProjected(income.getIncomeDate()),
                income.getCreatedAt(),
                conversions);
    }

    /** True when the date is after today in UTC (the cut is the UTC midnight, plan.md). */
    public static boolean isProjected(LocalDate incomeDate) {
        return incomeDate.isAfter(LocalDate.now(ZoneOffset.UTC));
    }
}
