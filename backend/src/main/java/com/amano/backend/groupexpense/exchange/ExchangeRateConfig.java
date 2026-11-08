package com.amano.backend.groupexpense.exchange;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring of the BCRA exchange rate client and its bounded in-memory cache
 * decorator (022 D8). Uses the standard JDK {@link HttpClient} with configured
 * connect and read timeouts and no extra dependencies.
 */
@Configuration
public class ExchangeRateConfig {

    @Bean
    public BcraExchangeRateClient bcraExchangeRateClient(
            @Value("${amano.exchange.bcra.base-url:https://api.bcra.gob.ar}") String baseUrl,
            @Value("${amano.exchange.bcra.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${amano.exchange.bcra.read-timeout-ms:5000}") long readTimeoutMs,
            @Value("${amano.exchange.bcra.max-attempts:2}") int maxAttempts) {
        return new BcraExchangeRateClient(bcraRestClient(baseUrl, connectTimeoutMs, readTimeoutMs), maxAttempts);
    }

    /**
     * RestClient for the public BCRA API with the configured timeouts. Shared with
     * the 027 currency catalog ({@code com.amano.backend.currency.CurrencyConfig}).
     */
    public static RestClient bcraRestClient(String baseUrl, long connectTimeoutMs, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(100, readTimeoutMs)));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @Primary
    public ExchangeRateProvider exchangeRateProvider(
            BcraExchangeRateClient bcraExchangeRateClient,
            @Value("${amano.exchange.bcra.cache-max-size:1000}") int cacheMaxSize) {
        return new CachingExchangeRateProvider(bcraExchangeRateClient, cacheMaxSize);
    }
}
