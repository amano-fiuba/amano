package com.amano.backend.payment;

/**
 * State of a payment (022 D6). The 018 flow creates payments already
 * {@code CONFIRMED}; when the group requires validation the 022 flow reports
 * them and an administrator later confirms or rejects them.
 *
 * <p>Only {@code CONFIRMED} payments count as applied by the 018 flow. The
 * {@code legacy} flag of {@link Payment} keeps the pre-022 rows outside the
 * partial unique index on {@code debt_id}.
 */
public enum PaymentStatus {
    PENDING,
    REPORTED,
    CONFIRMED,
    REJECTED
}
