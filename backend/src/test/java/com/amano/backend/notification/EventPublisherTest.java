package com.amano.backend.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.amano.backend.notification.dto.NotificationTargetResponse;

/** 035 T036: the publishing entry point never leaks a failure to the business flow. */
class EventPublisherTest {

    private static NotificationEvent event(NotificationType type) {
        return new NotificationEvent(7L, type, "Título", "Mensaje",
                new NotificationTargetResponse("GROUP", 42L, null, null, null),
                NotificationEvent.EmailPolicy.SUBSCRIBABLE);
    }

    @Test
    void publishesTheEventToTheApplicationContext() {
        ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
        NotificationEventPublisher publisher = new NotificationEventPublisher(spring);
        NotificationEvent event = event(NotificationType.GROUP_EXPENSE);

        publisher.publishSafely(event);

        verify(spring).publishEvent(event);
    }

    @Test
    void aPublishingFailureIsSwallowed() {
        ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
        org.mockito.Mockito.doThrow(new RuntimeException("publicación caída"))
                .when(spring).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
        NotificationEventPublisher publisher = new NotificationEventPublisher(spring);

        assertThatCode(() -> publisher.publishSafely(event(NotificationType.DEBT_OVERDUE)))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpPublisherIsSafeForLegacyConstructors() {
        assertThatCode(() -> NotificationEventPublisher.noOp().publishSafely(event(NotificationType.REMINDER)))
                .doesNotThrowAnyException();
    }
}
