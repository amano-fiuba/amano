package com.amano.backend.groupexpense.exchange;

import java.time.LocalDate;

import com.amano.backend.user.Currency;

/**
 * Domain port for historical currency exchange rates (022 D8). Implementations
 * return the official quote for {@code date} or the most recent previous
 * business day up to 7 days back, and throw
 * {@link ExchangeRateUnavailableException} when no quote can be obtained.
 */
public interface ExchangeRateProvider {

    /**
     * Resolves the conversion rate from {@code from} to {@code to} on
     * {@code date} (or up to 7 days back on weekends/holidays).
     */
    ExchangeRateQuote getQuote(Currency from, Currency to, LocalDate date);
}
