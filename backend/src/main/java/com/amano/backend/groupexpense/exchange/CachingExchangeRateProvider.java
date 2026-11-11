package com.amano.backend.groupexpense.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.amano.backend.user.Currency;

/**
 * Bounded in-memory LRU cache decorator over {@link ExchangeRateProvider}
 * (022 D8, T052). Keyed by {@code (from, to, date)}; historical quotes never
 * change, so there is no TTL. Also caches under the effective {@code rateDate}
 * when a weekend/holiday resolves to an earlier business day.
 */
public class CachingExchangeRateProvider implements ExchangeRateProvider {

    private final ExchangeRateProvider delegate;
    private final Map<CacheKey, ExchangeRateQuote> cache;

    record CacheKey(Currency from, Currency to, LocalDate date) {
    }

    public CachingExchangeRateProvider(ExchangeRateProvider delegate, int maxSize) {
        if (delegate == null) {
            throw new IllegalArgumentException("El proveedor de cotizaciones es obligatorio");
        }
        int capacity = Math.max(1, maxSize);
        this.delegate = delegate;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, ExchangeRateQuote> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public ExchangeRateQuote getQuote(Currency from, Currency to, LocalDate date) {
        if (from == null || to == null || date == null) {
            throw new IllegalArgumentException("Monedas y fecha son obligatorias");
        }
        if (from == to) {
            return new ExchangeRateQuote(BigDecimal.ONE, date, BcraExchangeRateClient.SOURCE_SAME_CURRENCY);
        }
        CacheKey key = new CacheKey(from, to, date);
        synchronized (cache) {
            ExchangeRateQuote cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        ExchangeRateQuote fetched = delegate.getQuote(from, to, date);
        synchronized (cache) {
            cache.put(key, fetched);
            if (fetched.rateDate() != null && !fetched.rateDate().equals(date)) {
                cache.put(new CacheKey(from, to, fetched.rateDate()), fetched);
            }
        }
        return fetched;
    }

    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    int size() {
        synchronized (cache) {
            return cache.size();
        }
    }
}
