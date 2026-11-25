package com.amano.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers notification events only after the originating business transaction
 * commits (feature 035, plan D4). {@code fallbackExecution} also covers jobs
 * that publish outside a transaction. Any delivery failure is logged here as a
 * last line of defence; it never reaches the business flow.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationDeliveryService deliveryService;

    public NotificationEventListener(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(NotificationEvent event) {
        try {
            deliveryService.deliver(event);
        }
        catch (RuntimeException exception) {
            log.error("Failed to deliver {} notification for user {}: {}",
                    event.type(), event.userId(), exception.getMessage());
        }
    }
}
