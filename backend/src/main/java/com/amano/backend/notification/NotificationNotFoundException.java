package com.amano.backend.notification;

/**
 * Raised when a notification id does not exist or belongs to another person.
 * Both cases share the same neutral message (FR-031, SC-008) so the API never
 * reveals whether a foreign notification exists.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("No se encontró la notificación");
    }
}
