package com.amano.backend.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.user.User;

/**
 * Token lifecycle for email verification (task T018, decisions D2/D9 of
 * research.md). The clear token is generated with {@link SecureRandom} and only
 * ever travels inside the emailed link; the database stores its SHA-256 hash.
 *
 * <p>One current token per account (FR-009/FR-021): issuing a new one marks the
 * previous ones as used in the same transaction, which is the one opened by
 * {@code RegistrationService.register} (data-model.md "Reglas de negocio").
 */
@Service
public class VerificationTokenService {

    /** ENTROPY_BYTES ⇒ 256 bits of randomness, Base64URL without padding. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final VerificationTokenRepository repository;
    private final VerificationProperties properties;

    public VerificationTokenService(VerificationTokenRepository repository,
            VerificationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Issues a fresh single-use token for the account and invalidates the current
     * ones, returning the clear value to be embedded in the email (never persisted).
     */
    @Transactional
    public String issueFor(User user) {
        OffsetDateTime now = OffsetDateTime.now();

        List<VerificationToken> current = repository.findByUserIdAndUsedAtIsNull(user.getId());
        current.forEach(token -> token.setUsedAt(now));
        repository.saveAll(current);

        byte[] entropy = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(entropy);
        String clearToken = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(clearToken));
        token.setExpiresAt(now.plus(properties.tokenExpiration()));
        repository.save(token);

        return clearToken;
    }

    /** SHA-256 hex of the clear token: the only form that reaches the database (D2). */
    public String hash(String clearToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(clearToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available in every JVM", impossible);
        }
    }
}
