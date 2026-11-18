package com.amano.backend.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.user.Currency;

/**
 * Converts subtotals to the calculation currency, asking each quote once per
 * (currency, date) within the request; a subtotal without a quote is dropped and
 * its movements counted as excluded (specs/032 FR-009, FR-010; 034 plan D5).
 * One instance per request: it is not thread-safe.
 */
public final class StatisticsConverter {

    private record QuoteKey(Currency from, LocalDate date) {
    }

    private final Currency target;
    private final ExchangeRateProvider exchangeRateProvider;
    private final Map<QuoteKey, Optional<BigDecimal>> rates = new HashMap<>();
    private long excluded;

    public StatisticsConverter(Currency target, ExchangeRateProvider exchangeRateProvider) {
        this.target = target;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    public Currency target() {
        return target;
    }

    /** Movements left out so far for lack of a quote. */
    public long excluded() {
        return excluded;
    }

    public Optional<BigDecimal> convert(Currency currency, LocalDate date, BigDecimal amount,
            long count) {
        if (currency == target) {
            return Optional.of(amount);
        }
        Optional<BigDecimal> rate = rates.computeIfAbsent(new QuoteKey(currency, date), this::quote);
        if (rate.isEmpty()) {
            excluded += count;
            return Optional.empty();
        }
        return Optional.of(amount.multiply(rate.get()));
    }

    private Optional<BigDecimal> quote(QuoteKey key) {
        try {
            return Optional.of(exchangeRateProvider.getQuote(key.from(), target, key.date()).rate());
        }
        catch (ExchangeRateUnavailableException unavailable) {
            return Optional.empty();
        }
    }
}
