package com.amano.backend.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@code users}. The unique index {@code uq_users_email} (FR-005)
 * remains the race-proof backstop; service code resolves conflicts via
 * {@code DataIntegrityViolationException}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Accounts to purge in the 3-day cleanup (task T055, FR-017, SC-007, D6): never
     * verified, with NO valid token (unused and unexpired) and whose MOST RECENT
     * token was issued before {@code cutoff}. The "no valid token" guard is
     * redundant while {@code token-expiration} (24 h) is shorter than
     * {@code account-expiration} (3 days), but it keeps the query faithful to
     * data-model "sin token vigente". Accounts without any token are never
     * selected: the rule is anchored to "the last token".
     */
    @Query("""
            select u from User u
            where u.emailVerifiedAt is null
              and not exists (
                  select 1 from VerificationToken t
                  where t.user = u and t.usedAt is null and t.expiresAt > :now
              )
              and (select max(t.createdAt) from VerificationToken t where t.user = u) < :cutoff
            """)
    List<User> findExpirableUnverifiedAccounts(@Param("now") OffsetDateTime now,
            @Param("cutoff") OffsetDateTime cutoff);
}
