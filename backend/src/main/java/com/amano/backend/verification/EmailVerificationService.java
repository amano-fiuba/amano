package com.amano.backend.verification;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.VerificationTokenExpiredException;
import com.amano.backend.common.VerificationTokenInvalidException;
import com.amano.backend.common.VerificationTokenUsedException;
import com.amano.backend.user.User;

/**
 * Redemption of a verification link (task T031, FR-005..FR-009, FR-021, D2).
 *
 * <p>Looks the token up by its SHA-256 hash (never by the clear value), enforces
 * the two rules of the model — {@code used_at IS NULL} and {@code expires_at >
 * now()} — and, only when both hold, marks the token as used and the account as
 * verified inside the same transaction, so a crash between both writes is
 * impossible (data-model.md "Reglas de negocio").
 *
 * <p>{@code noRollbackFor} on the expired branch is deliberate: FR-021 wants the
 * tardy redemption to <em>record</em> its use (so the link cannot be retried)
 * even though the response is the 410 error raised right afterwards.
 */
@Service
public class EmailVerificationService {

    private final VerificationTokenRepository tokenRepository;
    private final VerificationTokenService tokenService;

    public EmailVerificationService(VerificationTokenRepository tokenRepository,
            VerificationTokenService tokenService) {
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
    }

    /**
     * Redeems a clear token and returns its now-verified owner.
     *
     * @throws VerificationTokenInvalidException unknown/tampered token (400)
     * @throws VerificationTokenUsedException    already consumed token (409)
     * @throws VerificationTokenExpiredException past the 24 h window (410)
     */
    @Transactional(noRollbackFor = VerificationTokenExpiredException.class)
    public User redeem(String clearToken) {
        String tokenHash = tokenService.hash(clearToken == null ? "" : clearToken);
        VerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(VerificationTokenInvalidException::new);

        OffsetDateTime now = OffsetDateTime.now();
        if (token.getUsedAt() != null) {
            throw new VerificationTokenUsedException();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            token.setUsedAt(now);
            tokenRepository.save(token);
            throw new VerificationTokenExpiredException();
        }

        token.setUsedAt(now);
        tokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerifiedAt(now);
        return user;
    }
}
