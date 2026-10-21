package com.amano.backend.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Session JWT configuration, sourced from the environment:
 * {@code JWT_SECRET} (signing key) and {@code JWT_EXPIRATION} (seconds, 24 h by default).
 * Task T011; contract Set-Cookie section of contracts/auth-api.md.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("86400") @DurationUnit(ChronoUnit.SECONDS) Duration expiration) {

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and be at least 32 bytes long (HS256 key)");
        }
        if (expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("JWT_EXPIRATION must be a positive duration");
        }
    }
}
