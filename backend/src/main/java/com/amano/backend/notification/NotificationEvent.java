package com.amano.backend.notification;

import com.amano.backend.notification.dto.NotificationTargetResponse;

/**
 * Transient event published by an existing business flow after a successful
 * operation (035 contracts/notifications-api.md, "Generación de notificaciones").
 * It is never persisted: the listener delivers it after the originating
 * transaction commits. The target is structured data so the UI never parses
 * the message text to resolve a deep link.
 */
public record NotificationEvent(
        Long userId,
        NotificationType type,
        String title,
        String message,
        NotificationTargetResponse target,
        EmailPolicy emailPolicy) {

    /**
     * {@code SUBSCRIBABLE} respects the email channel preference;
     * {@code ALWAYS} is used for critical events (whistles and due dates) and
     * {@code NONE} for flows that already send their own business email.
     */
    public enum EmailPolicy {
        NONE,
        SUBSCRIBABLE,
        ALWAYS
    }
}
