package com.amano.backend.groupexpense.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.amano.backend.user.Currency;

/**
 * 022 D8 / T052: unit tests for {@link CachingExchangeRateProvider} and
 * {@link ExchangeRateConfig}.
 */
class CachingExchangeRateProviderTest {

    @Test
    void cachesByPairAndDateAndAvoidsSecondDelegateCall() {
        ExchangeRateProvider delegate = mock(ExchangeRateProvider.class);
        LocalDate sunday = LocalDate.of(2026, 1, 18);
        LocalDate friday = LocalDate.of(2026, 1, 16);
        ExchangeRateQuote expected = new ExchangeRateQuote(
                new BigDecimal("18.50000000"), friday, "BCRA");
        when(delegate.getQuote(Currency.USD, Currency.ARS, sunday)).thenReturn(expected);

        CachingExchangeRateProvider cache = new CachingExchangeRateProvider(delegate, 2);

        assertThat(cache.getQuote(Currency.USD, Currency.ARS, sunday)).isEqualTo(expected);
        assertThat(cache.getQuote(Currency.USD, Currency.ARS, sunday)).isEqualTo(expected);
        // Also cached under the effective rateDate (Friday)
        assertThat(cache.getQuote(Currency.USD, Currency.ARS, friday)).isEqualTo(expected);

        verify(delegate, times(1)).getQuote(Currency.USD, Currency.ARS, sunday);
    }

    @Test
    void evictsLeastRecentlyUsedEntriesWhenMaxSizeIsExceeded() {
        ExchangeRateProvider delegate = mock(ExchangeRateProvider.class);
        LocalDate d1 = LocalDate.of(2026, 1, 10);
        LocalDate d2 = LocalDate.of(2026, 1, 11);
        LocalDate d3 = LocalDate.of(2026, 1, 12);
        when(delegate.getQuote(Currency.USD, Currency.ARS, d1))
                .thenReturn(new ExchangeRateQuote(new BigDecimal("10.00000000"), d1, "BCRA"));
        when(delegate.getQuote(Currency.USD, Currency.ARS, d2))
                .thenReturn(new ExchangeRateQuote(new BigDecimal("11.00000000"), d2, "BCRA"));
        when(delegate.getQuote(Currency.USD, Currency.ARS, d3))
                .thenReturn(new ExchangeRateQuote(new BigDecimal("12.00000000"), d3, "BCRA"));

        CachingExchangeRateProvider cache = new CachingExchangeRateProvider(delegate, 2);

        cache.getQuote(Currency.USD, Currency.ARS, d1);
        cache.getQuote(Currency.USD, Currency.ARS, d2);
        // Touch d1 so d2 becomes the least recently used
        cache.getQuote(Currency.USD, Currency.ARS, d1);
        cache.getQuote(Currency.USD, Currency.ARS, d3);

        assertThat(cache.size()).isEqualTo(2);
        // d1 is still cached; d2 was evicted and must be fetched again
        cache.getQuote(Currency.USD, Currency.ARS, d1);
        cache.getQuote(Currency.USD, Currency.ARS, d2);

        verify(delegate, times(1)).getQuote(Currency.USD, Currency.ARS, d1);
        verify(delegate, times(2)).getQuote(Currency.USD, Currency.ARS, d2);

        cache.clear();
        assertThat(cache.size()).isZero();
    }

    @Test
    void sameCurrencyReturnsOneWithoutCallingTheDelegate() {
        ExchangeRateProvider delegate = mock(ExchangeRateProvider.class);
        CachingExchangeRateProvider cache = new CachingExchangeRateProvider(delegate, 10);
        LocalDate date = LocalDate.of(2026, 1, 15);

        ExchangeRateQuote quote = cache.getQuote(Currency.ARS, Currency.ARS, date);

        assertThat(quote.rate()).isEqualByComparingTo("1");
        assertThat(quote.rateSource()).isEqualTo("SAME_CURRENCY");
        verifyNoInteractions(delegate);
    }

    @Test
    void validatesArgumentsAndWiringConfig() {
        assertThatThrownBy(() -> new CachingExchangeRateProvider(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        CachingExchangeRateProvider cache = new CachingExchangeRateProvider(mock(ExchangeRateProvider.class), 10);
        assertThatThrownBy(() -> cache.getQuote(null, Currency.ARS, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);

        ExchangeRateConfig config = new ExchangeRateConfig();
        BcraExchangeRateClient client = config.bcraExchangeRateClient(
                "https://api.bcra.gob.ar", 1000, 1000, 2);
        ExchangeRateProvider provider = config.exchangeRateProvider(client, 100);
        assertThat(provider).isInstanceOf(CachingExchangeRateProvider.class);
    }
}
