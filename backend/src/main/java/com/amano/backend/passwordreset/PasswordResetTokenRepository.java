package com.amano.backend.passwordreset;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link PasswordResetToken} (feature 006).
 * Queries are derived: no SQL/JPQL is declared here.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Current (unused) tokens of an account; issuing a new one invalidates them (FR-007). */
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);

    /** Redemption lookup: tokens are searched by hash, never by clear value (FR-011). */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Successful reset: every reset token of the account goes, so none can be reused (FR-015). */
    void deleteByUserId(Long userId);
}
