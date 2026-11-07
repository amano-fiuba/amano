package com.amano.backend.payment;

/**
 * 022 D6: the debt already has a reported or settled payment, so a new payment
 * with a different idempotency key is rejected with 409 (contract
 * {@code payment-validation.md}).
 */
public class DebtAlreadyHasPaymentException extends RuntimeException {

    public static final String MESSAGE = "Esta deuda ya tiene un pago registrado";
    public static final String CODE = "DEBT_ALREADY_HAS_PAYMENT";

    public DebtAlreadyHasPaymentException() {
        super(MESSAGE);
    }
}
