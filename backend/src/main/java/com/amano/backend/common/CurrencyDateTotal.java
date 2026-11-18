package com.amano.backend.common;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.amano.backend.user.Currency;

/**
 * Sum of the amounts of one currency on one date: the grouping the personal balance
 * (specs/032-income-management) converts, so it asks for one quote per
 * {@code (currency, date)} instead of one per movement.
 */
public record CurrencyDateTotal(Currency currency, LocalDate date, BigDecimal total) {
}
