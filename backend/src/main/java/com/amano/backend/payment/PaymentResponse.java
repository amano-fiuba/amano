package com.amano.backend.payment;

import java.time.OffsetDateTime;

import com.amano.backend.groupexpense.GroupExpenseResponse;

/**
 * Representation of a registered payment (018 plan.md §Contrato REST): the
 * payment plus the debt in its current state. {@code payer} and
 * {@code receiver}/{@code registeredBy} reuse the 017 shapes so the frontend
 * reads debts the same way in the expense detail and in the payment.
 * {@code status} (additive, 022) is {@code CONFIRMED} when the payment was added to the
 * debt at once or {@code REPORTED} when the group requires validation and an
 * administrator still has to decide.
 */
public record PaymentResponse(
        Long id,
        Long groupId,
        Long expenseId,
        Long debtId,
        String amount,
        String currency,
        String date,
        GroupExpenseResponse.Debtor payer,
        GroupExpenseResponse.Person receiver,
        GroupExpenseResponse.Person registeredBy,
        OffsetDateTime createdAt,
        GroupExpenseResponse.DebtView debt,
        String status) {
}
