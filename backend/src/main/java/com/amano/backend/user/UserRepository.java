package com.amano.backend.user;

import java.util.Collection;
import java.util.Set;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
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

    /**
     * User search (feature 008, D2): partial, case-insensitive match on first
     * name, last name or email, ONLY verified accounts, never the caller.
     * {@code term} arrives already trimmed and lowercased. locate() matches the
     * text literally, so '%' and '_' are NOT wildcards. Ordered by first name,
     * then last name (missing last names last), then id for a stable order; the
     * caller caps the size with {@code pageable} (20).
     */
    @Query("""
            select u from User u
            where u.emailVerifiedAt is not null
              and u.id <> :currentUserId
              and (locate(:term, lower(u.firstName)) > 0
                   or locate(:term, lower(coalesce(u.lastName, ''))) > 0
                   or locate(:term, lower(u.email)) > 0)
            order by lower(u.firstName), lower(u.lastName) nulls last, u.id
            """)
    List<User> searchVerified(@Param("term") String term,
            @Param("currentUserId") Long currentUserId, Pageable pageable);

    /** Accounts with a custom avatar among {@code ids}, without loading the binaries. */
    @Query("select a.id from UserAvatar a where a.id in :ids")
    Set<Long> findIdsWithAvatar(@Param("ids") Collection<Long> ids);
}
