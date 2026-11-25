package com.amano.backend.notification.dto;

import java.util.List;

/**
 * Body of {@code GET /api/v1/notifications}. {@code unreadCount} is the total
 * number of unread notices of the user, independent of {@code limit}; it drives
 * the header badge. {@code nextCursor} is null on the last page.
 */
public record NotificationListResponse(
        List<NotificationResponse> notifications,
        long unreadCount,
        String nextCursor) {
}
