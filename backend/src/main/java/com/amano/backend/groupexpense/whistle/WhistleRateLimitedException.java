package com.amano.backend.groupexpense.whistle;

/**
 * 030 FR-007: the pair (group, creditor, debtor) already has a whistle sent or
 * in progress in the last 24 h — 429 with {@code Retry-After}.
 */
public class WhistleRateLimitedException extends RuntimeException {

    public static final String CODE = "WHISTLE_RATE_LIMITED";

    private final long retryAfterSeconds;

    public WhistleRateLimitedException(String debtorName, long retryAfterSeconds) {
        super("Ya le silbaste a " + debtorName + " en las últimas 24 horas");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
