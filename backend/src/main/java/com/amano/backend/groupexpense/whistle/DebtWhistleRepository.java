package com.amano.backend.groupexpense.whistle;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence of {@link DebtWhistle} (030). {@link #findBlocking} backs the
 * 24 h limit (FR-007) through {@code ix_debt_whistles_pair}; the conditional
 * updates only move a row that is still {@code SENDING}, like the reminders
 * of 025.
 */
public interface DebtWhistleRepository extends JpaRepository<DebtWhistle, Long> {

    /**
     * Attempts of the pair (group, creditor, debtor) that still count for the
     * limit — {@code SENDING} or {@code SENT} created after {@code since} —
     * newest first. {@code FAILED} never blocks (FR-008).
     */
    @Query("""
            SELECT w FROM DebtWhistle w
            WHERE w.groupId = :groupId
              AND w.creditorUserId = :creditorId
              AND w.debtorUserId = :debtorId
              AND w.status IN (com.amano.backend.groupexpense.whistle.DebtWhistleStatus.SENDING,
                  com.amano.backend.groupexpense.whistle.DebtWhistleStatus.SENT)
              AND w.createdAt > :since
            ORDER BY w.createdAt DESC
            """)
    List<DebtWhistle> findBlocking(@Param("groupId") Long groupId, @Param("creditorId") Long creditorId,
            @Param("debtorId") Long debtorId, @Param("since") OffsetDateTime since);

    /** {@code SENDING → SENT}; returns 0 when the row was already decided. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DebtWhistle w
            SET w.status = com.amano.backend.groupexpense.whistle.DebtWhistleStatus.SENT,
                w.sentAt = :now, w.updatedAt = :now
            WHERE w.id = :id
              AND w.status = com.amano.backend.groupexpense.whistle.DebtWhistleStatus.SENDING
            """)
    int markSent(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /** {@code SENDING → FAILED}; a failed attempt no longer blocks the pair. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DebtWhistle w
            SET w.status = com.amano.backend.groupexpense.whistle.DebtWhistleStatus.FAILED,
                w.updatedAt = :now
            WHERE w.id = :id
              AND w.status = com.amano.backend.groupexpense.whistle.DebtWhistleStatus.SENDING
            """)
    int markFailed(@Param("id") Long id, @Param("now") OffsetDateTime now);
}
