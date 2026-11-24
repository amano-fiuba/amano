package com.amano.backend.notification;

import java.util.Map;

/**
 * Raised when a preferences payload is missing a flag or has an invalid type.
 * The handler answers 400 with {@code properties.fields} (035 contract), with
 * field names as map keys and messages in Spanish.
 */
public class InvalidNotificationPreferencesException extends RuntimeException {

    private final Map<String, String> fields;

    public InvalidNotificationPreferencesException(Map<String, String> fields) {
        super("Las preferencias de notificación contienen campos inválidos");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
