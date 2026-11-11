package com.amano.backend.payment;

/**
 * 022 D6: the debt is settled or already has a payment waiting for validation, so
 * a new payment is rejected with 409 (contract
 * {@code payment-validation.md}).
 */
public class DebtAlreadyHasPaymentException extends RuntimeException {

    public static final String MESSAGE = "Esta deuda ya tiene un pago registrado";
    /** The debt has a payment waiting for an administrator (partial payments, 022 D6). */
    public static final String AWAITING_VALIDATION_MESSAGE =
            "Esta deuda ya tiene un pago esperando validación: esperá a que un administrador lo decida";
    public static final String CODE = "DEBT_ALREADY_HAS_PAYMENT";

    public DebtAlreadyHasPaymentException() {
        super(MESSAGE);
    }

    private DebtAlreadyHasPaymentException(String message) {
        super(message);
    }

    /** 409 for a debt that already has a payment waiting for validation. */
    public static DebtAlreadyHasPaymentException awaitingValidation() {
        return new DebtAlreadyHasPaymentException(AWAITING_VALIDATION_MESSAGE);
    }
}
