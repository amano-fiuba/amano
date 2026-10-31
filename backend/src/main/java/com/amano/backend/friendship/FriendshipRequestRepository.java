package com.amano.backend.friendship;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;

/**
 * Persistence for {@code friendship_requests} (features 011 and 012). The
 * partial unique indexes of V6 are the race-proof backstop for the pair
 * invariants; the service queries below only produce readable 409s before
 * inserting. The conditional UPDATE is the single mutation of 012 (research.md D5).
 */
public interface FriendshipRequestRepository extends JpaRepository<FriendshipRequest, Long> {

    /**
     * True when a request with {@code status} exists between the two accounts in
     * EITHER direction (FR-005/FR-006): a pending request is duplicated when the
     * other person already sent one, and a friendship exists regardless of who sent it.
     */
    @Query("""
            select case when count(r) > 0 then true else false end
            from FriendshipRequest r
            where r.status = :status
              and ((r.senderId = :a and r.recipientId = :b)
                or (r.senderId = :b and r.recipientId = :a))
            """)
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b,
            @Param("status") FriendshipRequestStatus status);

    /** Inbox of the session user: pending requests received, newest first (FR-012). */
    List<FriendshipRequest> findByRecipientIdAndStatusOrderByCreatedAtDescIdDesc(
            Long recipientId, FriendshipRequestStatus status);

    /**
     * Atomic transition of a pending request owned by {@code recipientId} to a
     * final status (feature 012, FR-011): id, ownership and PENDING preconditions
     * live in the same statement so exactly one concurrent response wins and the
     * rest affect zero rows (research.md D5).
     */
    @Modifying
    @Query("""
            update FriendshipRequest r
            set r.status = :newStatus, r.respondedAt = :respondedAt
            where r.id = :requestId
              and r.recipientId = :recipientId
              and r.status = PENDING
            """)
    int respondPending(@Param("requestId") Long requestId,
            @Param("recipientId") Long recipientId,
            @Param("newStatus") FriendshipRequestStatus newStatus,
            @Param("respondedAt") OffsetDateTime respondedAt);
}
