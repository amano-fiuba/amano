package com.amano.backend.groupexpense.whistle;

/**
 * 030 Q10: the mail could not be delivered (SMTP failure or no resolvable
 * address) — 503. The attempt is marked FAILED and does not count for the limit.
 */
public class WhistleDeliveryException extends RuntimeException {

    public static final String CODE = "WHISTLE_DELIVERY_FAILED";
    public static final String MESSAGE = "No pudimos enviar el aviso. Probá de nuevo en unos minutos";

    public WhistleDeliveryException() {
        super(MESSAGE);
    }
}
