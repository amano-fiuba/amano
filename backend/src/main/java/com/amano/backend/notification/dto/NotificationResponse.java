package com.amano.backend.notification.dto;

import java.time.OffsetDateTime;

import com.amano.backend.notification.NotificationType;

/** Body of a notification in the list and of {@code PATCH .../{id}/read}. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime readAt,
        NotificationTargetResponse target) {
}
