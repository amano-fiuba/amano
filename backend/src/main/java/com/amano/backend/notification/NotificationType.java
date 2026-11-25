package com.amano.backend.notification;

/**
 * Event kinds that can produce an in-app notification (035 contracts/notifications-api.md).
 * Stored as {@code VARCHAR} through {@code @Enumerated(EnumType.STRING)}.
 */
public enum NotificationType {
    REMINDER,
    DEBT_WHISTLE,
    DEBT_OVERDUE,
    PAYER_DUE_NOTICE,
    GROUP_EXPENSE,
    PAYMENT_RECORDED,
    FRIEND_REQUEST,
    FRIEND_ACTIVITY
}
