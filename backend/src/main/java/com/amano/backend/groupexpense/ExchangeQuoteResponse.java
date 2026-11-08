package com.amano.backend.groupexpense;

import java.math.BigDecimal;

/**
 * Read-only exchange quote preview for a group (022 T069): rate from the
 * requested currency to the group's primary currency (scale 8), the effective
 * date of the quote, its source ({@code BCRA}, or {@code SAME_CURRENCY} when no
 * conversion applies) and the group's currency.
 */
public record ExchangeQuoteResponse(
        BigDecimal rate,
        String rateDate,
        String rateSource,
        String groupCurrency) {
}
