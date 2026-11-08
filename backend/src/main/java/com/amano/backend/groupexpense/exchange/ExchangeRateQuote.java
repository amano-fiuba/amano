package com.amano.backend.groupexpense.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Historical exchange rate resolved for an expense (022 D8).
 *
 * @param rate       rate from the expense currency to the group's primary
 *                   currency (scale 8 when converted, 1 when same currency)
 * @param rateDate   effective date of the quote (may be an earlier business day)
 * @param rateSource source identifier ({@code "BCRA"} or {@code "SAME_CURRENCY"})
 */
public record ExchangeRateQuote(
        BigDecimal rate,
        LocalDate rateDate,
        String rateSource) {
}
