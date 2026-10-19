package com.amano.backend.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates session JWTs (claims {@code sub}/{@code iat}/{@code exp}),
 * signed HS256 with {@code JWT_SECRET}. Task T011; decision D1 of research.md.
 */
public class JwtService {

    private final SecretKey key;
    private final java.time.Duration expiration;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    public String issueToken(long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration.toMillis(), ChronoUnit.MILLIS)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the authenticated user id, or empty when the token is missing, tampered
     * or expired (invalid tokens must never authenticate a request).
     */
    public Optional<Long> parseSubject(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        }
        catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public java.time.Duration expiration() {
        return expiration;
    }
}
