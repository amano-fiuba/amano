package com.amano.backend.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 027 T011: 24 h cache of the catalog that survives BCRA outages once filled. */
class CachingCurrencyCatalogProviderTest {

    private static final List<CurrencyCatalogEntry> FIRST = List.of(new CurrencyCatalogEntry("ARS", "PESO"));
    private static final List<CurrencyCatalogEntry> SECOND = List.of(
            new CurrencyCatalogEntry("ARS", "PESO"), new CurrencyCatalogEntry("BRL", "REAL"));

    private CurrencyCatalogProvider delegate;
    private MutableClock clock;
    private CachingCurrencyCatalogProvider provider;

    @BeforeEach
    void setUp() {
        delegate = mock(CurrencyCatalogProvider.class);
        clock = new MutableClock(Instant.parse("2026-11-08T12:00:00Z"));
        provider = new CachingCurrencyCatalogProvider(delegate, Duration.ofHours(24), clock);
    }

    @Test
    void servesTheCachedCatalogWithinTheTtl() {
        when(delegate.getCatalog()).thenReturn(FIRST);

        assertThat(provider.getCatalog()).isEqualTo(FIRST);
        clock.advance(Duration.ofHours(23));
        assertThat(provider.getCatalog()).isEqualTo(FIRST);

        verify(delegate, times(1)).getCatalog();
    }

    @Test
    void refreshesAfterTheTtl() {
        when(delegate.getCatalog()).thenReturn(FIRST, SECOND);

        provider.getCatalog();
        clock.advance(Duration.ofHours(24));

        assertThat(provider.getCatalog()).isEqualTo(SECOND);
        verify(delegate, times(2)).getCatalog();
    }

    @Test
    void servesTheExpiredCatalogWhenTheRefreshFails() {
        when(delegate.getCatalog()).thenReturn(FIRST).thenThrow(new CurrencyCatalogUnavailableException());

        provider.getCatalog();
        clock.advance(Duration.ofDays(2));

        assertThat(provider.getCatalog()).isEqualTo(FIRST);
    }

    @Test
    void propagatesTheFailureWhenNothingIsCached() {
        when(delegate.getCatalog()).thenThrow(new CurrencyCatalogUnavailableException());

        assertThatThrownBy(provider::getCatalog).isInstanceOf(CurrencyCatalogUnavailableException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
