package com.amano.backend.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One reported payment awaiting an administrator's decision (022 contract
 * {@code payment-validation.md}, {@code GET .../payments/pending-validation}).
 * {@code payerUserId} is null when the payer is a simulated profile.
 */
public record PendingPaymentDetail(
        Long paymentId,
        Long debtId,
        Long expenseId,
        Long payerUserId,
        String payerName,
        String receiverName,
        BigDecimal amount,
        String currency,
        OffsetDateTime registeredAt,
        int daysPending) {
}
