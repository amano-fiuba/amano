package com.amano.backend.notification.dto;

/**
 * Body of {@code GET} and {@code PUT /api/v1/notifications/preferences}: two
 * channels and four event types, all enabled by default.
 */
public record NotificationPreferencesResponse(
        Channels channels,
        EventTypes eventTypes) {

    public record Channels(boolean inApp, boolean email) {
    }

    public record EventTypes(
            boolean remindersAndWhistles,
            boolean debtDueAndPendingCollections,
            boolean groupActivity,
            boolean friendActivity) {
    }
}
