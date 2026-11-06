package com.amano.backend.payment;

/** 018 FR-006: the debt is already PAID; answered 409 with a machine-readable code. */
public class DebtAlreadyPaidException extends RuntimeException {

    public static final String MESSAGE = "La deuda ya está pagada";
    public static final String CODE = "DEBT_ALREADY_PAID";

    public DebtAlreadyPaidException() {
        super(MESSAGE);
    }
}
