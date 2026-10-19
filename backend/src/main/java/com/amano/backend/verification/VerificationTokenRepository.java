package com.amano.backend.verification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link VerificationToken} (data-model.md, feature 003).
 * Queries are derived: no SQL/JPQL is declared here.
 */
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /** Redemption lookup: tokens are searched by hash, never by clear value (D2). */
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /** Current (unused) tokens of an account; issuing a new one invalidates them (FR-009). */
    List<VerificationToken> findByUserIdAndUsedAtIsNull(Long userId);
}
