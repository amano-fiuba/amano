package com.amano.backend.groups;

/**
 * Spec "Alcance y limitaciones" motive 2 — deudas pendientes (FR-010): the
 * target still has pending debts with other group members, so the removal is
 * blocked. Consulted through {@link com.amano.backend.groups.port.DebtPort};
 * since 017 US2 the real {@code LedgerDebtPort} answers from the group debts
 * (any PENDING or PARTIALLY_PAID debt, as debtor or creditor).
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
