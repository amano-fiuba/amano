package com.amano.backend.notification.dto;

/**
 * Structured deep-link destination of a notification (035 contract). The UI
 * resolves the route from {@code kind} and the ids, never from the message text.
 * Missing or invalid ids are represented by {@code NONE}.
 */
public record NotificationTargetResponse(
        String kind,
        Long groupId,
        Long debtId,
        Long expenseId,
        Long friendRequestId) {

    public static NotificationTargetResponse none() {
        return new NotificationTargetResponse("NONE", null, null, null, null);
    }
}
