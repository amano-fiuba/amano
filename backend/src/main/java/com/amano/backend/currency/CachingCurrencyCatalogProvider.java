package com.amano.backend.currency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * In-memory cache of the currency catalog (027, contracts/currencies-api.md):
 * served for {@code ttl} (24 h by default); after that it is refreshed, and if
 * the refresh fails the expired copy is still served. Without any copy the
 * failure propagates as {@link CurrencyCatalogUnavailableException}.
 */
public class CachingCurrencyCatalogProvider implements CurrencyCatalogProvider {

    private final CurrencyCatalogProvider delegate;
    private final Duration ttl;
    private final Clock clock;

    private List<CurrencyCatalogEntry> cached;
    private Instant fetchedAt;

    public CachingCurrencyCatalogProvider(CurrencyCatalogProvider delegate, Duration ttl, Clock clock) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public synchronized List<CurrencyCatalogEntry> getCatalog() {
        Instant now = clock.instant();
        if (cached != null && now.isBefore(fetchedAt.plus(ttl))) {
            return cached;
        }
        try {
            cached = List.copyOf(delegate.getCatalog());
            fetchedAt = now;
            return cached;
        }
        catch (CurrencyCatalogUnavailableException failure) {
            if (cached != null) {
                return cached;
            }
            throw failure;
        }
    }
}
