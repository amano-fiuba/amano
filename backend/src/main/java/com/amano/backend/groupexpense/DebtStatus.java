package com.amano.backend.groupexpense;

import java.math.BigDecimal;

/**
 * State of a debt (017 research.md D5), always derived from the paid amount:
 * {@code PENDING} (nothing paid), {@code PARTIALLY_PAID} (between zero and the
 * amount) and {@code PAID} (the whole amount). Transitions only move forward;
 * the {@code ck_debts_status_matches_paid} CHECK of V13 enforces the same rule.
 */
public enum DebtStatus {
    PENDING,
    PARTIALLY_PAID,
    PAID;

    /** Status that corresponds to {@code paid} of {@code amount}; rejects impossible values. */
    public static DebtStatus of(BigDecimal amount, BigDecimal paid) {
        if (amount == null || paid == null || paid.signum() < 0 || paid.compareTo(amount) > 0) {
            throw new IllegalArgumentException("El monto pagado no es válido para la deuda");
        }
        if (paid.signum() == 0) {
            return PENDING;
        }
        return paid.compareTo(amount) == 0 ? PAID : PARTIALLY_PAID;
    }

    /** True once nothing is left to pay; PENDING and PARTIALLY_PAID are unsettled. */
    public boolean isSettled() {
        return this == PAID;
    }
}
