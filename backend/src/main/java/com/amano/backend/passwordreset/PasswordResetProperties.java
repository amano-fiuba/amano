package com.amano.backend.passwordreset;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Password reset deadlines, sourced from {@code application.properties}
 * (feature 006, FR-006): the token expires after 1 hour. Unitless values are
 * read as seconds, like {@code VerificationProperties}.
 */
@ConfigurationProperties(prefix = "password-reset")
public record PasswordResetProperties(
        @DefaultValue("3600") @DurationUnit(ChronoUnit.SECONDS) Duration tokenExpiration) {

    public PasswordResetProperties {
        if (tokenExpiration.isNegative() || tokenExpiration.isZero()) {
            throw new IllegalStateException("password-reset.token-expiration must be a positive duration");
        }
    }
}
