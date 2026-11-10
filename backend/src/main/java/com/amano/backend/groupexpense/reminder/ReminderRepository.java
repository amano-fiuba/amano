package com.amano.backend.groupexpense.reminder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence of {@link Reminder} (025). The conditional updates are the
 * concurrency backstop: a row can be claimed, sent, failed or cancelled only
 * while it is still {@code SCHEDULED} and, for the programmable operations,
 * never claimed.
 */
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByGroupIdAndStatus(Long groupId, ReminderStatus status);

    List<Reminder> findByGroupIdAndStatusAndClaimedAtIsNull(Long groupId, ReminderStatus status);

    List<Reminder> findByGroupIdAndStatusAndClaimedAtIsNotNull(Long groupId, ReminderStatus status);

    List<Reminder> findByGroupIdAndStatusAndSendDateLessThanEqual(Long groupId, ReminderStatus status,
            LocalDate date);

    boolean existsByKindAndDebtId(ReminderKind kind, Long debtId);

    boolean existsByKindAndExpenseId(ReminderKind kind, Long expenseId);

    Optional<Reminder> findByKindAndDebtId(ReminderKind kind, Long debtId);

    Optional<Reminder> findByKindAndExpenseId(ReminderKind kind, Long expenseId);

    @Query("SELECT DISTINCT r.groupId FROM Reminder r WHERE r.status = :status")
    List<Long> findDistinctGroupIdsByStatus(@Param("status") ReminderStatus status);

    /** Claims a never-claimed scheduled row; a second run gets 0 and does not send. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Reminder r
            SET r.claimedAt = :now, r.updatedAt = :now
            WHERE r.id = :id
              AND r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.SCHEDULED
              AND r.claimedAt IS NULL
            """)
    int claim(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /** Marks a claimed row as sent, storing the subject/body snapshot. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Reminder r
            SET r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.SENT,
                r.sentAt = :sentAt, r.subject = :subject, r.body = :body, r.updatedAt = :now
            WHERE r.id = :id
              AND r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.SCHEDULED
            """)
    int markSent(@Param("id") Long id, @Param("sentAt") OffsetDateTime sentAt,
            @Param("subject") String subject, @Param("body") String body, @Param("now") OffsetDateTime now);

    /** Marks a claimed row as failed; it is never retried. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Reminder r
            SET r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.FAILED,
                r.updatedAt = :now
            WHERE r.id = :id
              AND r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.SCHEDULED
            """)
    int markFailed(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /** Cancels a scheduled row that was never claimed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Reminder r
            SET r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.CANCELLED,
                r.updatedAt = :now
            WHERE r.id = :id
              AND r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.SCHEDULED
              AND r.claimedAt IS NULL
            """)
    int cancel(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /** Reschedules a scheduled row that was never claimed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Reminder r
            SET r.sendDate = :sendDate, r.updatedAt = :now
            WHERE r.id = :id
              AND r.status = com.amano.backend.groupexpense.reminder.ReminderStatus.SCHEDULED
              AND r.claimedAt IS NULL
            """)
    int reschedule(@Param("id") Long id, @Param("sendDate") LocalDate sendDate,
            @Param("now") OffsetDateTime now);
}
