package com.amano.backend.groups;

/**
 * Spec "Alcance y limitaciones" motive 4 — último admin (US5 y US10): the
 * operation would leave the group without an active admin, so it is blocked.
 * The UI suggests assigning another admin first.
 */
public class LastAdminException extends RuntimeException {

    public static final String MESSAGE =
            "Sos el último administrador: asigná otro administrador antes de salir";

    public LastAdminException() {
        super(MESSAGE);
    }

    public LastAdminException(String message) {
        super(message);
    }
}
