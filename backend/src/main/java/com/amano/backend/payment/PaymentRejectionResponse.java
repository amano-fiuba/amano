package com.amano.backend.payment;

import com.amano.backend.groupexpense.DebtStatus;

/**
 * Result of rejecting a reported payment (022 contract
 * {@code payment-validation.md}): the payment is now {@code REJECTED}, the
 * debt returns to {@code PENDING} (or to the legacy {@code PARTIALLY_PAID}
 * state when an earlier 018 payment existed) and the administrator that
 * decided is recorded.
 */
public record PaymentRejectionResponse(
        Long paymentId,
        PaymentStatus status,
        DebtStatus debtStatusAfter,
        Long rejectedByUserId) {
}
