package com.amano.backend.groupexpense.whistle;

/**
 * 030 FR-002/FR-009: the debt cannot be whistled right now — it has no balance,
 * its payment waits for validation, or the debtor left the group. 409 with a
 * machine-readable {@link #getCode() code}.
 */
public class DebtNotWhistleableException extends RuntimeException {

    public static final String NOT_WHISTLEABLE = "DEBT_NOT_WHISTLEABLE";
    public static final String DEBTOR_NOT_MEMBER = "DEBTOR_NOT_MEMBER";

    private final String code;

    public DebtNotWhistleableException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
