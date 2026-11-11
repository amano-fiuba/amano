package com.amano.backend.payment;

import java.time.OffsetDateTime;

import com.amano.backend.groupexpense.DebtStatus;

/**
 * Result of registering a full debt payment (022 contract
 * {@code payment-validation.md}): the payment state and the debt state after
 * the registration. {@code decidedByUserId} and {@code decidedAt} stay null
 * until an administrator confirms or rejects a reported payment.
 */
public record PaymentRegistrationResponse(
        Long paymentId,
        PaymentStatus status,
        DebtStatus debtStatusAfter,
        Long decidedByUserId,
        OffsetDateTime decidedAt) {
}
