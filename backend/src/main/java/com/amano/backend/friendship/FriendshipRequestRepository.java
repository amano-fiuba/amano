package com.amano.backend.friendship;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;

/**
 * Persistence for {@code friendship_requests} (features 011, 012 and 013). The
 * partial unique indexes of V12 are the race-proof backstop for the pair
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
     * Friends of {@code userId} (feature 013, research.md D3/D4): the OTHER account
     * of every ACCEPTED request the user takes part in, in either direction, with
     * the acceptance date. One query, projected straight to the DTO so private
     * account data is never loaded; same ordering as the user search of 008.
     */
    @Query("""
            select new com.amano.backend.friendship.FriendResponse(
                u.id, u.firstName, u.lastName, u.email, r.respondedAt)
            from FriendshipRequest r
            join User u on u.id = case when r.senderId = :userId
                                       then r.recipientId else r.senderId end
            where r.status = ACCEPTED
              and (r.senderId = :userId or r.recipientId = :userId)
            order by lower(u.firstName), lower(u.lastName) nulls last, u.id
            """)
    List<FriendResponse> findFriendsOf(@Param("userId") Long userId);

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
