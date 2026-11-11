package com.amano.backend.payment;

/**
 * 022 US5: the payment does not exist, belongs to another group or the caller
 * is not an active member of its group. Every case answers the same neutral
 * 404, so the existence of payments of other groups is never revealed.
 */
public class PaymentNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No existe el pago solicitado o no sos miembro del grupo";

    public PaymentNotFoundException() {
        super(MESSAGE);
    }
}
