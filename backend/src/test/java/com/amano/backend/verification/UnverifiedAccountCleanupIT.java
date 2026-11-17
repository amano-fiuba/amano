package com.amano.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.amano.backend.support.AbstractIntegrationIT;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Contract tests of the unverified-account cleanup (task T055, TDD: written
 * BEFORE the implementation) with a real PostgreSQL and a SHORTENED deadline
 * ({@code verification.account-expiration=2s}) so the 3-day rule is provable in
 * milliseconds. FR-017/FR-018/SC-007 + data-model "Limpieza (3 días)": delete the
 * unverified accounts with no valid token whose most recent token is older than
 * the deadline, cascade their tokens and FREE the address; the resend limits are
 * NOT removed (no FK, they persist as a hash-only footprint).
 */
@TestPropertySource(properties = "verification.account-expiration=2s")
class UnverifiedAccountCleanupIT extends AbstractIntegrationIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private EmailResendLimitRepository resendLimitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UnverifiedAccountCleanupScheduler scheduler;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM users"); // tokens cascade by FK
        jdbcTemplate.update("DELETE FROM email_resend_limits");
    }

    private static User account(String email, OffsetDateTime verifiedAt) {
        User user = new User();
        user.setFirstName("Ana");
        user.setLastName("Pérez");
        user.setEmail(email);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setTermsAcceptedAt(OffsetDateTime.now());
        user.setEmailVerifiedAt(verifiedAt);
        return user;
    }

    /**
     * Persists a token and forces its {@code created_at}/{@code expires_at}: the
     * entity stamps {@code created_at} on insert and the DB CHECK forbids
     * {@code expires_at <= created_at}, so the backdate is a raw UPDATE.
     */
    private VerificationToken token(User user, OffsetDateTime createdAt, OffsetDateTime expiresAt,
            OffsetDateTime usedAt) {
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(user.getEmail() + "|" + createdAt + "|" + expiresAt));
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));
        token.setUsedAt(usedAt);
        VerificationToken saved = tokenRepository.save(token);
        jdbcTemplate.update(
                "UPDATE email_verification_tokens SET created_at = ?, expires_at = ? WHERE id = ?",
                createdAt, expiresAt, saved.getId());
        return saved;
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** FR-017/FR-018/SC-007: expired unverified account → account and token gone, limit kept. */
    @Test
    void deletesAnExpiredUnverifiedAccountAndCascadesItsTokens() {
        User expired = userRepository.save(account("expirada@ejemplo.com", null));
        VerificationToken old = token(expired, OffsetDateTime.now().minusSeconds(10),
                OffsetDateTime.now().minusSeconds(9), null);
        // The click that started the term left a hash-only row that SURVIVES (data-model).
        EmailResendLimit limit = new EmailResendLimit();
        limit.setEmailHash(sha256Hex("expirada@ejemplo.com"));
        limit.setLastResendAt(OffsetDateTime.now().minusDays(1));
        resendLimitRepository.save(limit);

        scheduler.cleanupExpiredUnverifiedAccounts();

        assertThat(userRepository.findByEmail("expirada@ejemplo.com")).isEmpty();
        assertThat(tokenRepository.findById(old.getId())).isEmpty(); // FR-018: old links are invalid
        assertThat(resendLimitRepository.findById(sha256Hex("expirada@ejemplo.com"))).isPresent();
        // SC-007: the address is free again, so a full re-registration is possible.
        assertThat(userRepository.save(account("expirada@ejemplo.com", null)).getId()).isNotNull();
    }

    /** The last token is fresh → the account is NOT removed (only > deadline is). */
    @Test
    void keepsAnUnverifiedAccountWhoseMostRecentTokenIsFresh() {
        User fresh = userRepository.save(account("fresca@ejemplo.com", null));
        // An old, already-used token plus a brand new one: the MOST RECENT one decides.
        token(fresh, OffsetDateTime.now().minusSeconds(10), OffsetDateTime.now().minusSeconds(9),
                OffsetDateTime.now().minusSeconds(9));
        token(fresh, OffsetDateTime.now(), OffsetDateTime.now().plusHours(24), null);

        scheduler.cleanupExpiredUnverifiedAccounts();

        assertThat(userRepository.findByEmail("fresca@ejemplo.com")).isPresent();
    }

    /** The account is verified → never touched, even with an ancient last token (FR-017). */
    @Test
    void keepsAVerifiedAccountEvenWhenItsLastTokenIsOld() {
        User verified = userRepository.save(
                account("verificada@ejemplo.com", OffsetDateTime.now().minusMinutes(5)));
        token(verified, OffsetDateTime.now().minusSeconds(10), OffsetDateTime.now().minusSeconds(9),
                OffsetDateTime.now().minusSeconds(9));

        scheduler.cleanupExpiredUnverifiedAccounts();

        assertThat(userRepository.findByEmail("verificada@ejemplo.com")).isPresent();
    }
}
