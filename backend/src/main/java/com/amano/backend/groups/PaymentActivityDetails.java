package com.amano.backend.groups;

import java.math.BigDecimal;

/**
 * Data of a payment between group members for the {@code PAYMENT_MADE} history
 * event (014 US2, data-model.md). Used by the future group-payments module
 * through {@link ActivityEventService#recordPaymentMade}. Names are copied at
 * the time of the event (preservation pattern).
 *
 * @param paymentId id of the payment (optional)
 * @param amount    amount, &gt; 0 (required)
 * @param currency  ISO 4217 code (required)
 * @param fromName  visible name of who pays (optional)
 * @param toName    visible name of who receives (optional)
 */
public record PaymentActivityDetails(Long paymentId, BigDecimal amount, String currency, String fromName,
        String toName) {
}
