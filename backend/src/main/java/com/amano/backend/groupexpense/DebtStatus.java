package com.amano.backend.groupexpense;

import java.math.BigDecimal;

/**
 * State of a debt (017 research.md D5, extended by 022 D3).
 *
 * <p>{@code PENDING} means nothing paid, {@code PARTIALLY_PAID} that part of the
 * debt was paid and a balance remains (partial payments, 018 and 022),
 * {@code PAYMENT_REPORTED} that a payment waits for an administrator (and
 * {@code paidAmount} is untouched), {@code PAID} is a debt fully paid without
 * validation, {@code VERIFIED} one whose last payment an administrator
 * confirmed, and {@code OVERDUE} is a pending debt past the group deadline.
 *
 * <p>{@code PAID} and {@code VERIFIED} are settled; {@code PAYMENT_REPORTED} is
 * not. The {@code ck_debts_status} and {@code ck_debts_status_matches_paid}
 * CHECKs of V13 are dropped and recreated with these six values in V18.
 */
public enum DebtStatus {
    PENDING,
    PARTIALLY_PAID,
    PAYMENT_REPORTED,
    PAID,
    VERIFIED,
    OVERDUE;

    /**
     * Status that corresponds to {@code paid} of {@code amount} for the 018
     * flow; rejects impossible values. It produces only
     * {@code PENDING}/{@code PARTIALLY_PAID}/{@code PAID}; {@code PAYMENT_REPORTED},
     * {@code VERIFIED} and {@code OVERDUE} are set explicitly by their flows.
     */
    public static DebtStatus of(BigDecimal amount, BigDecimal paid) {
        if (amount == null || paid == null || paid.signum() < 0 || paid.compareTo(amount) > 0) {
            throw new IllegalArgumentException("El monto pagado no es válido para la deuda");
        }
        if (paid.signum() == 0) {
            return PENDING;
        }
        return paid.compareTo(amount) == 0 ? PAID : PARTIALLY_PAID;
    }

    /** True once nothing is left to pay; PENDING, PARTIALLY_PAID, PAYMENT_REPORTED and OVERDUE are unsettled. */
    public boolean isSettled() {
        return this == PAID || this == VERIFIED;
    }

    /** True while a new payment can be registered: pending, partial or overdue. */
    public boolean isPayable() {
        return this == PENDING || this == PARTIALLY_PAID || this == OVERDUE;
    }
}
