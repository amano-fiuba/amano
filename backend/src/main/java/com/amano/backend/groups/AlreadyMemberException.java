package com.amano.backend.groups;

import java.util.Map;

import com.amano.backend.common.FieldValidationException;

/**
 * Spec "Alcance y limitaciones" motive 1 — ya es miembro (FR-007): the person
 * to add already belongs to the group. 400 with a clear reason; the detail
 * message is exactly the one the UI renders.
 */
public class AlreadyMemberException extends FieldValidationException {

    public static final String MESSAGE = "Esa persona ya es miembro del grupo";

    public AlreadyMemberException() {
        super(Map.of("identifier", MESSAGE));
    }
}
