package com.amano.backend.verification;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Verification deadlines, sourced from {@code application.properties} (task T004,
 * contracts "Plazos"): token expiration 24 h, resend cooldown 5 min, unverified
 * account expiry 3 days and the hourly cleanup cron (D6, D8). Unitless values are
 * read as seconds via {@link DurationUnit}; ITs override them with short values
 * without touching the logic (research.md section 4).
 */
@ConfigurationProperties(prefix = "verification")
public record VerificationProperties(
        @DefaultValue("86400") @DurationUnit(ChronoUnit.SECONDS) Duration tokenExpiration,
        @DefaultValue("300") @DurationUnit(ChronoUnit.SECONDS) Duration resendCooldown,
        @DefaultValue("259200") @DurationUnit(ChronoUnit.SECONDS) Duration accountExpiration,
        @DefaultValue("0 0 * * * *") String cronCleanup) {

    public VerificationProperties {
        if (tokenExpiration.isNegative() || tokenExpiration.isZero()) {
            throw new IllegalStateException("verification.token-expiration must be a positive duration");
        }
        if (resendCooldown.isNegative() || resendCooldown.isZero()) {
            throw new IllegalStateException("verification.resend-cooldown must be a positive duration");
        }
        if (accountExpiration.isNegative() || accountExpiration.isZero()) {
            throw new IllegalStateException("verification.account-expiration must be a positive duration");
        }
        if (cronCleanup == null || cronCleanup.isBlank()) {
            throw new IllegalStateException("verification.cron-cleanup must be a cron expression");
        }
    }
}
