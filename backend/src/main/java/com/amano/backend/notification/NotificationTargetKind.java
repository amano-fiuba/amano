package com.amano.backend.notification;

/**
 * Kind of destination stored with a notification. The UI resolves the deep link
 * from this value and the plain target ids, never from the message text.
 */
public enum NotificationTargetKind {
    GROUP,
    DEBT,
    FRIEND_REQUEST,
    NONE
}
