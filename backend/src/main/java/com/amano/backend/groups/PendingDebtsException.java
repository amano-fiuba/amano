package com.amano.backend.groups;

/**
 * Spec "Alcance y limitaciones" motive 2 — deudas pendientes (FR-010): the
 * target still has pending debts with other group members, so the removal is
 * blocked. Consulted through {@link DebtPort}; with the NoOp stub this only
 * becomes observable end-to-end once the debts module exists.
 */
public class PendingDebtsException extends RuntimeException {

    public static final String MESSAGE =
            "No se permite la baja: la persona tiene deudas pendientes con otros miembros del grupo";

    public PendingDebtsException() {
        super(MESSAGE);
    }

    public PendingDebtsException(String message) {
        super(message);
    }
}
