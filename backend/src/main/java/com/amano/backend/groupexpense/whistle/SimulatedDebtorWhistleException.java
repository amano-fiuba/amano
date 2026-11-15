package com.amano.backend.groupexpense.whistle;

/** 030 FR-004 (Q3): simulated profiles never receive whistles — 422. */
public class SimulatedDebtorWhistleException extends RuntimeException {

    public static final String CODE = "SIMULATED_DEBTOR";
    public static final String MESSAGE = "Los perfiles simulados no reciben silbidos";

    public SimulatedDebtorWhistleException() {
        super(MESSAGE);
    }
}
