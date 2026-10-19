package com.amano.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import com.amano.backend.config.JwtProperties;
import com.amano.backend.config.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of the session JWT service (task T012; FR-007, FR-016, SC-005).
 * Contract: claims sub/iat/exp, 86400 s expiration, HS256 signature with JWT_SECRET.
 */
class JwtServiceTest {

    /** 32+ bytes so hmacShaKeyFor accepts it as an HS256 key. */
    private static final String SECRET = "0dfb8b2afee541e19b93mnode7a1c443c85b0ecbce6d4c65f3dcb3bfa64a4b3c";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new com.amano.backend.config.JwtProperties(SECRET, Duration.ofSeconds(86400)));
    }

    @Test
    void issuesTokenCarryingUserIdAsSubject() {
        String token = jwtService.issueToken(42L);

        Optional<Long> subject = jwtService.parseSubject(token);
        assertThat(subject).contains(42L);
    }

    @Test
    void tokenExpiresExactly24HoursAfterIssuance() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = jwtService.issueToken(7L);
        Instant after = Instant.now();

        io.jsonwebtoken.Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(java.time.Duration.ofHours(24).toMillis());
        assertThat(claims.getIssuedAt().toInstant()).isAfterOrEqualTo(before);
        assertThat(Date.from(after)).isAfterOrEqualTo(claims.getIssuedAt());
    }

    @Test
    void signatureIsVerifiedAgainstTheConfiguredSecret() {
        String token = jwtService.issueToken(7L);

        SecretKey otherSecret = Keys.hmacShaKeyFor(
                "another-secret-value-used-just-for-testing-purposes-ff"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Jwts.parser()
                .verifyWith(otherSecret)
                .build()
                .parseSignedClaims(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issueToken(7L);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwtService.parseSubject(tampered)).isEmpty();
    }

    @Test
    void rejectsBlankAndNullTokens() {
        assertThat(jwtService.parseSubject(null)).isEmpty();
        assertThat(jwtService.parseSubject("")).isEmpty();
        assertThat(jwtService.parseSubject("   ")).isEmpty();
    }

    @Test
    void rejectsConfiguredSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new com.amano.backend.config.JwtProperties(
                "too-short", Duration.ofSeconds(86400)))
                .isInstanceOf(IllegalStateException.class);
    }
}
