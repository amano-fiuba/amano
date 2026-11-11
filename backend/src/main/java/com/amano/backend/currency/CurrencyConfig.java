package com.amano.backend.currency;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.amano.backend.groupexpense.exchange.ExchangeRateConfig;

/**
 * Wiring of the BCRA currency catalog and its cache (027, research.md R1). Reuses
 * the {@code amano.exchange.bcra.*} connection properties of 022.
 */
@Configuration
public class CurrencyConfig {

    @Bean
    public BcraCurrencyCatalogClient bcraCurrencyCatalogClient(
            @Value("${amano.exchange.bcra.base-url:https://api.bcra.gob.ar}") String baseUrl,
            @Value("${amano.exchange.bcra.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${amano.exchange.bcra.read-timeout-ms:5000}") long readTimeoutMs,
            @Value("${amano.exchange.bcra.max-attempts:2}") int maxAttempts) {
        return new BcraCurrencyCatalogClient(
                ExchangeRateConfig.bcraRestClient(baseUrl, connectTimeoutMs, readTimeoutMs), maxAttempts);
    }

    @Bean
    @Primary
    public CurrencyCatalogProvider currencyCatalogProvider(BcraCurrencyCatalogClient bcraCurrencyCatalogClient,
            @Value("${amano.exchange.bcra.catalog-ttl:PT24H}") Duration ttl, Clock clock) {
        return new CachingCurrencyCatalogProvider(bcraCurrencyCatalogClient, ttl, clock);
    }
}
