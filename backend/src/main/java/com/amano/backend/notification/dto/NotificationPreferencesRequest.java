package com.amano.backend.notification.dto;

/**
 * Body of {@code PUT /api/v1/notifications/preferences}. Every flag is required;
 * wrappers let the service distinguish a missing field from an explicit
 * {@code false} and answer 400 with {@code properties.fields}.
 */
public record NotificationPreferencesRequest(
        Channels channels,
        EventTypes eventTypes) {

    public record Channels(Boolean inApp, Boolean email) {
    }

    public record EventTypes(
            Boolean remindersAndWhistles,
            Boolean debtDueAndPendingCollections,
            Boolean groupActivity,
            Boolean friendActivity) {
    }
}
