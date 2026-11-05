package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Equal division of a group expense in whole cents (017 research.md D4,
 * FR-005/FR-006). With {@code T} cents and {@code n} participants every share
 * is {@code T / n}; the remainder {@code T mod n} goes entirely to the payer when
 * the payer participates (no debtor pays more than the exact division) and,
 * otherwise, one cent each to the first participants in request order. The sum
 * of the shares is always exactly the total. The frontend preview
 * ({@code splitEqually.js}) applies the same rule.
 */
public final class EqualSplit {

    private EqualSplit() {
    }

    /** True when the total gives at least one cent to each of {@code participants}. */
    public static boolean fits(BigDecimal total, int participants) {
        return total != null && participants > 0 && total.signum() > 0
                && total.movePointRight(2).longValue() >= participants;
    }

    /**
     * Shares in the same order as the participants.
     *
     * @param total        amount with at most two decimals
     * @param participants number of participants, at least one
     * @param payerIndex   position of the payer among the participants, or {@code -1}
     *                     when the payer does not participate
     */
    public static List<BigDecimal> split(BigDecimal total, int participants, int payerIndex) {
        if (participants < 1 || payerIndex < -1 || payerIndex >= participants) {
            throw new IllegalArgumentException("Participantes inválidos para dividir el gasto");
        }
        if (total == null || total.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto debe tener como máximo dos decimales");
        }
        if (!fits(total, participants)) {
            throw new IllegalArgumentException("El monto no alcanza para dividirlo entre los participantes");
        }
        long cents = total.movePointRight(2).longValueExact();
        long base = cents / participants;
        long remainder = cents % participants;

        List<BigDecimal> shares = new ArrayList<>(participants);
        for (int position = 0; position < participants; position++) {
            long share = base;
            if (payerIndex >= 0) {
                if (position == payerIndex) {
                    share += remainder;
                }
            }
            else if (position < remainder) {
                share += 1;
            }
            shares.add(BigDecimal.valueOf(share, 2));
        }
        return shares;
    }
}
