package com.amano.backend.payment;

/**
 * 022 US5: only a {@code REPORTED} non-legacy payment can be confirmed or
 * rejected. An already decided payment, a payment of the 018 flow or a payment
 * whose debt is no longer {@code PAYMENT_REPORTED} answers 409.
 */
public class PaymentNotDecidableException extends RuntimeException {

    public static final String MESSAGE = "Este pago no está pendiente de validación";
    public static final String CODE = "PAYMENT_NOT_DECIDABLE";

    public PaymentNotDecidableException() {
        super(MESSAGE);
    }
}
