package com.amano.backend.friendship;

/**
 * The session user still has a pending direct debt (a shared expense without a
 * group) with the friend (feature 016, FR-011..FR-013). The friendship is not
 * removed until the debt is settled.
 */
public class PendingDirectDebtException extends RuntimeException {

    public static final String MESSAGE =
            "No podés eliminar a este amigo: tenés una deuda pendiente. Saldá la deuda e intentá nuevamente.";

    public static final String CODE = "PENDING_DIRECT_DEBT";

    public PendingDirectDebtException() {
        super(MESSAGE);
    }
}
