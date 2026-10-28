package com.amano.backend.groups;

/**
 * Spec "Alcance y limitaciones" motive 5 — admin: primero quitar el rol (US4
 * clarification): an admin cannot remove another admin directly; the role must
 * be revoked first (then the ex-admin is a plain MEMBER).
 */
public class AdminRemovalBlockedException extends RuntimeException {

    public static final String MESSAGE =
            "No podés eliminar directamente a otro administrador: primero quitale el rol";

    public AdminRemovalBlockedException() {
        super(MESSAGE);
    }
}
