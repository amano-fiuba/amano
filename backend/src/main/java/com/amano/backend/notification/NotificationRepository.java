package com.amano.backend.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence of {@link Notification} (feature 035, plan D6). All reads and
 * writes filter by the owner id: a notification of another person is never
 * returned or modified, not even when its raw id is known.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Full history, newest first; used with a page size of {@code limit + 1}. */
    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /** Next history page after the opaque cursor (createdAt, id). */
    @Query("""
            select n from Notification n
            where n.userId = :userId
              and (n.createdAt < :before or (n.createdAt = :before and n.id < :beforeId))
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findHistoryBefore(@Param("userId") Long userId,
            @Param("before") OffsetDateTime before, @Param("beforeId") Long beforeId, Pageable pageable);

    /** Header badge: unread count independent of the requested limit. */
    long countByUserIdAndReadAtIsNull(Long userId);

    /** Popover: unread first, then newest; the cursor is not used in this order. */
    @Query("""
            select n from Notification n
            where n.userId = :userId
            order by case when n.readAt is null then 0 else 1 end, n.createdAt desc, n.id desc
            """)
    List<Notification> findUnreadFirst(@Param("userId") Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Idempotent: only an unread row changes; a second call affects 0 rows. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Notification n set n.readAt = :now
            where n.id = :id and n.userId = :userId and n.readAt is null
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /** Global action: only the caller's unread rows change. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
