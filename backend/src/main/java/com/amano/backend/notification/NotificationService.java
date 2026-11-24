package com.amano.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.notification.dto.NotificationListResponse;
import com.amano.backend.notification.dto.NotificationResponse;
import com.amano.backend.notification.dto.NotificationTargetResponse;

/**
 * Inbox reads and read-state changes of feature 035 (plan D6). Every operation
 * is scoped to the authenticated user; a foreign or unknown id is a neutral
 * {@link NotificationNotFoundException}. Marking is idempotent.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final String POPOVER_ORDER = "unread-first";

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public NotificationListResponse list(Long userId, Integer rawLimit, String before, String order) {
        int limit = normalizeLimit(rawLimit);
        Pageable page = PageRequest.of(0, limit + 1);
        List<Notification> rows;
        boolean unreadFirst = POPOVER_ORDER.equals(order);
        if (unreadFirst) {
            rows = repository.findUnreadFirst(userId, page);
        }
        else {
            Cursor cursor = decodeCursor(before);
            rows = cursor == null
                    ? repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, page)
                    : repository.findHistoryBefore(userId, cursor.createdAt(), cursor.id(), page);
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        String nextCursor = hasMore && !rows.isEmpty() ? encodeCursor(rows.get(rows.size() - 1)) : null;
        return new NotificationListResponse(rows.stream().map(NotificationService::toResponse).toList(),
                repository.countByUserIdAndReadAtIsNull(userId), nextCursor);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.markRead(OffsetDateTime.now(clock));
        return toResponse(repository.save(notification));
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, OffsetDateTime.now(clock));
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    private static int normalizeLimit(Integer raw) {
        if (raw == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, raw));
    }

    private static String encodeCursor(Notification notification) {
        String raw = notification.getCreatedAt().toInstant().toEpochMilli() + "|" + notification.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded),
                    java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                return null;
            }
            OffsetDateTime date = OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneOffset.UTC);
            return new Cursor(date, Long.valueOf(parts[1]));
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Cursor(OffsetDateTime createdAt, Long id) {
    }

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                targetOf(notification));
    }

    private static NotificationTargetResponse targetOf(Notification notification) {
        return switch (notification.getTargetKind()) {
            case GROUP -> new NotificationTargetResponse("GROUP", notification.getGroupId(), null,
                    notification.getExpenseId(), null);
            case DEBT -> new NotificationTargetResponse("DEBT", notification.getGroupId(),
                    notification.getDebtId(), notification.getExpenseId(), null);
            case FRIEND_REQUEST -> new NotificationTargetResponse("FRIEND_REQUEST", null, null, null,
                    notification.getFriendRequestId());
            case NONE -> NotificationTargetResponse.none();
        };
    }
}
