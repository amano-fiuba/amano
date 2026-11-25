package com.amano.backend.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.notification.dto.NotificationPreferencesRequest;
import com.amano.backend.notification.dto.NotificationPreferencesResponse;

/**
 * Reads and updates the settings of the session user (feature 035, plan D7).
 * A missing row is created with all six flags enabled on first access.
 */
@Service
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    public NotificationPreferencesResponse get(Long userId) {
        return toResponse(getOrCreate(userId));
    }

    @Transactional
    public NotificationPreferencesResponse update(Long userId, NotificationPreferencesRequest request) {
        Map<String, String> invalid = validate(request);
        if (!invalid.isEmpty()) {
            throw new InvalidNotificationPreferencesException(invalid);
        }
        UserNotificationPreferences preferences = getOrCreate(userId);

        NotificationPreferencesRequest.Channels channels = request.channels();
        NotificationPreferencesRequest.EventTypes events = request.eventTypes();
        preferences.setInApp(channels.inApp());
        preferences.setEmail(channels.email());
        preferences.setRemindersWhistles(events.remindersAndWhistles());
        preferences.setDebtDueCollections(events.debtDueAndPendingCollections());
        preferences.setGroupActivity(events.groupActivity());
        preferences.setFriendActivity(events.friendActivity());
        return toResponse(repository.save(preferences));
    }

    /** Used by the delivery service: one row per person, created lazily with defaults. */
    @Transactional
    public UserNotificationPreferences getOrCreate(Long userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> repository.save(UserNotificationPreferences.defaultsFor(userId)));
    }

    private static Map<String, String> validate(NotificationPreferencesRequest request) {
        Map<String, String> invalid = new LinkedHashMap<>();
        if (request == null || request.channels() == null || request.eventTypes() == null) {
            invalid.put("channels", "Los canales y los tipos de evento son obligatorios");
            return invalid;
        }
        require(invalid, "channels.inApp", request.channels().inApp());
        require(invalid, "channels.email", request.channels().email());
        require(invalid, "eventTypes.remindersAndWhistles", request.eventTypes().remindersAndWhistles());
        require(invalid, "eventTypes.debtDueAndPendingCollections",
                request.eventTypes().debtDueAndPendingCollections());
        require(invalid, "eventTypes.groupActivity", request.eventTypes().groupActivity());
        require(invalid, "eventTypes.friendActivity", request.eventTypes().friendActivity());
        return invalid;
    }

    private static void require(Map<String, String> invalid, String field, Boolean value) {
        if (value == null) {
            invalid.put(field, "El campo es obligatorio");
        }
    }

    private static NotificationPreferencesResponse toResponse(UserNotificationPreferences preferences) {
        return new NotificationPreferencesResponse(
                new NotificationPreferencesResponse.Channels(preferences.isInApp(), preferences.isEmail()),
                new NotificationPreferencesResponse.EventTypes(
                        preferences.isRemindersWhistles(),
                        preferences.isDebtDueCollections(),
                        preferences.isGroupActivity(),
                        preferences.isFriendActivity()));
    }
}
