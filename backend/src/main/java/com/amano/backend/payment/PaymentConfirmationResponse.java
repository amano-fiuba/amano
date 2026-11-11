package com.amano.backend.payment;

import com.amano.backend.groupexpense.DebtStatus;

/**
 * Result of confirming a reported payment (022 contract
 * {@code payment-validation.md}): the payment is now {@code CONFIRMED}, the
 * debt is {@code VERIFIED} (settled and equivalent to {@code PAID}) and the
 * administrator that decided is recorded.
 */
public record PaymentConfirmationResponse(
        Long paymentId,
        PaymentStatus status,
        DebtStatus debtStatusAfter,
        Long confirmedByUserId) {
}
