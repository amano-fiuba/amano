package com.amano.backend.common;

import java.util.Map;

/**
 * Raised by service-level validations performed after normalization (trim/lowercase,
 * clarifications Q3/Q4), so the handler can answer 400 with per-field messages
 * (properties.fields, contracts/auth-api.md "Errores"). Map keys are field names.
 */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public FieldValidationException(Map<String, String> fields) {
        super("La solicitud contiene campos inválidos");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
