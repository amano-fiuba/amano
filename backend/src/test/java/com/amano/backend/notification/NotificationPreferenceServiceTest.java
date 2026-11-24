package com.amano.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amano.backend.notification.dto.NotificationPreferencesRequest;
import com.amano.backend.notification.dto.NotificationPreferencesResponse;

/** 035 T018: defaults, six Boolean flags and validation of the PUT body. */
@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    private static final long USER = 7L;

    @Mock
    private NotificationPreferenceRepository repository;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository);
    }

    @Test
    void firstReadCreatesAllFlagsEnabled() {
        when(repository.findByUserId(USER)).thenReturn(Optional.empty());
        when(repository.save(any(UserNotificationPreferences.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferencesResponse result = service.get(USER);

        assertThat(result.channels().inApp()).isTrue();
        assertThat(result.channels().email()).isTrue();
        assertThat(result.eventTypes().remindersAndWhistles()).isTrue();
        assertThat(result.eventTypes().debtDueAndPendingCollections()).isTrue();
        assertThat(result.eventTypes().groupActivity()).isTrue();
        assertThat(result.eventTypes().friendActivity()).isTrue();
        verify(repository).save(any(UserNotificationPreferences.class));
    }

    @Test
    void updatePersistsTheSixFlagsAndIgnoresAnyClientUserId() {
        UserNotificationPreferences stored = UserNotificationPreferences.defaultsFor(USER);
        when(repository.findByUserId(USER)).thenReturn(Optional.of(stored));
        when(repository.save(any(UserNotificationPreferences.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferencesResponse result = service.update(USER, request(false, false, true, false));

        assertThat(result.channels().inApp()).isTrue();
        assertThat(result.channels().email()).isFalse();
        assertThat(result.eventTypes().remindersAndWhistles()).isTrue();
        assertThat(result.eventTypes().debtDueAndPendingCollections()).isFalse();
        assertThat(result.eventTypes().groupActivity()).isTrue();
        assertThat(result.eventTypes().friendActivity()).isFalse();
        assertThat(stored.getUserId()).isEqualTo(USER);
    }

    @Test
    void aMissingBooleanIsRejectedWithItsFieldName() {
        NotificationPreferencesRequest bad = new NotificationPreferencesRequest(
                new NotificationPreferencesRequest.Channels(true, null),
                new NotificationPreferencesRequest.EventTypes(true, true, true, true));

        assertThatThrownBy(() -> service.update(USER, bad))
                .isInstanceOf(InvalidNotificationPreferencesException.class)
                .satisfies(error -> assertThat(((InvalidNotificationPreferencesException) error).getFields())
                        .containsKey("channels.email"));
        verify(repository, never()).save(any());
    }

    @Test
    void aNullBodyIsRejectedBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> service.update(USER, null))
                .isInstanceOf(InvalidNotificationPreferencesException.class);
        verify(repository, never()).save(any());
    }

    private static NotificationPreferencesRequest request(boolean email, boolean debt, boolean group,
            boolean friends) {
        return new NotificationPreferencesRequest(
                new NotificationPreferencesRequest.Channels(true, email),
                new NotificationPreferencesRequest.EventTypes(true, debt, group, friends));
    }
}
