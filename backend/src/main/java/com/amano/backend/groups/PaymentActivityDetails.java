package com.amano.backend.groups;

import java.math.BigDecimal;

/**
 * Data of a payment between group members for the {@code PAYMENT_MADE},
 * {@code PAYMENT_REGISTERED}, {@code PAYMENT_CONFIRMED} and
 * {@code PAYMENT_REJECTED} history events (014 US2; 018 D6; 022 US8 T047/T049).
 * Names are copied at the time of the event (preservation pattern).
 *
 * @param paymentId         id of the payment (optional)
 * @param amount            amount, &gt; 0 (required)
 * @param currency          ISO 4217 code (required)
 * @param fromName          visible name of who pays (optional)
 * @param toName            visible name of who receives (optional)
 * @param expenseId         group expense of the paid debt (optional, 018 plan.md D6)
 * @param debtId            paid debt (optional, 018 plan.md D6)
 * @param debtStatusBefore  debt status before the transition (optional, 022 Q7)
 * @param debtStatusAfter   debt status after the transition (optional, 022 Q7)
 */
public record PaymentActivityDetails(Long paymentId, BigDecimal amount, String currency, String fromName,
        String toName, Long expenseId, Long debtId, String debtStatusBefore, String debtStatusAfter) {

    /** The 014 format, without the expense, debt and status links. */
    public PaymentActivityDetails(Long paymentId, BigDecimal amount, String currency, String fromName,
            String toName) {
        this(paymentId, amount, currency, fromName, toName, null, null, null, null);
    }

    /** The 018 format, with expense and debt links. */
    public PaymentActivityDetails(Long paymentId, BigDecimal amount, String currency, String fromName,
            String toName, Long expenseId, Long debtId) {
        this(paymentId, amount, currency, fromName, toName, expenseId, debtId, null, null);
    }
}
