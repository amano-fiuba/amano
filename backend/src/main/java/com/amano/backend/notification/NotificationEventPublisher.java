package com.amano.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Safe publishing entry point used by the existing business services (plan D4).
 * Publishing must never make an otherwise successful operation fail.
 */
@Component
public class NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    public NotificationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** No-op publisher for legacy constructors kept for unit tests. */
    public static NotificationEventPublisher noOp() {
        return new NotificationEventPublisher(event -> {
        });
    }

    public void publishSafely(NotificationEvent event) {
        try {
            publisher.publishEvent(event);
        }
        catch (RuntimeException exception) {
            log.warn("Could not publish {} notification for user {}: {}",
                    event.type(), event.userId(), exception.getMessage());
        }
    }
}
