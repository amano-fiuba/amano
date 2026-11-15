package com.amano.backend.groupexpense.whistle;

/** 030 FR-003: only the creditor of the debt may whistle it — 403. */
public class WhistleNotCreditorException extends RuntimeException {

    public static final String CODE = "WHISTLE_NOT_CREDITOR";
    public static final String MESSAGE = "Solo quien recibe el pago puede avisar esta deuda";

    public WhistleNotCreditorException() {
        super(MESSAGE);
    }
}
