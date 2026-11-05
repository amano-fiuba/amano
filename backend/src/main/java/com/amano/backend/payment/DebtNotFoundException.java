package com.amano.backend.payment;

/**
 * 018 FR-003: the debt does not exist or the caller is not an active member of
 * its group. Both cases answer the same neutral 404, so the existence of debts
 * of other groups is never revealed.
 */
public class DebtNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No existe la deuda solicitada o no sos miembro del grupo";

    public DebtNotFoundException() {
        super(MESSAGE);
    }
}
