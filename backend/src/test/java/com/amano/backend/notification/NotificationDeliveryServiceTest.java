package com.amano.backend.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amano.backend.notification.NotificationEvent.EmailPolicy;
import com.amano.backend.notification.dto.NotificationTargetResponse;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/** 035 T018: delivery failures must never escape to the business flow. */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    private static final long USER = 7L;

    @Mock
    private NotificationPreferenceService preferenceService;

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationMailService mailService;

    @Mock
    private UserRepository userRepository;

    private NotificationDeliveryService delivery;
    private UserNotificationPreferences defaults;

    @BeforeEach
    void setUp() {
        delivery = new NotificationDeliveryService(preferenceService, repository, mailService, userRepository);
        defaults = UserNotificationPreferences.defaultsFor(USER);
    }

    private void withUser() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("ana@example.com");
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
    }

    @Test
    void aPersistenceFailureIsLoggedAndDoesNotStopTheEmailChannel() {
        withUser();
        when(preferenceService.getOrCreate(USER)).thenReturn(defaults);
        when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("BD caída"));

        assertThatCode(() -> delivery.deliver(event(EmailPolicy.SUBSCRIBABLE))).doesNotThrowAnyException();
        verify(mailService).send("ana@example.com", NotificationType.GROUP_EXPENSE, "Título", "Mensaje");
    }

    @Test
    void anSmtpFailureNeverEscapesAndTheNoticeIsStillStored() {
        withUser();
        when(preferenceService.getOrCreate(USER)).thenReturn(defaults);
        doThrow(new RuntimeException("SMTP caído")).when(mailService)
                .send(anyString(), any(NotificationType.class), anyString(), anyString());

        assertThatCode(() -> delivery.deliver(event(EmailPolicy.SUBSCRIBABLE))).doesNotThrowAnyException();
        verify(repository).save(any(Notification.class));
    }

    @Test
    void anUnavailablePreferencesRepositoryIsSwallowed() {
        when(preferenceService.getOrCreate(USER)).thenThrow(new RuntimeException("BD caída"));

        assertThatCode(() -> delivery.deliver(event(EmailPolicy.SUBSCRIBABLE))).doesNotThrowAnyException();
        verify(repository, never()).save(any());
        verify(mailService, never()).send(anyString(), any(), anyString(), anyString());
    }

    @Test
    void aDisabledEventTypeProducesNothing() {
        defaults.setGroupActivity(false);
        when(preferenceService.getOrCreate(USER)).thenReturn(defaults);

        delivery.deliver(event(EmailPolicy.SUBSCRIBABLE));

        verify(repository, never()).save(any());
        verify(mailService, never()).send(anyString(), any(), anyString(), anyString());
    }

    @Test
    void emailPolicyNoneSuppressesEmailAndAlwaysOverridesTheChannelToggle() {
        withUser();
        when(preferenceService.getOrCreate(USER)).thenReturn(defaults);
        defaults.setEmail(false);

        delivery.deliver(event(EmailPolicy.NONE));
        verify(mailService, never()).send(anyString(), any(), anyString(), anyString());

        delivery.deliver(event(EmailPolicy.ALWAYS));
        verify(mailService).send("ana@example.com", NotificationType.GROUP_EXPENSE, "Título", "Mensaje");
    }

    private static NotificationEvent event(EmailPolicy policy) {
        return new NotificationEvent(USER, NotificationType.GROUP_EXPENSE, "Título", "Mensaje",
                new NotificationTargetResponse("GROUP", 42L, null, null, null), policy);
    }
}
