package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 022 D2/T023/T024: custom and percentage shares of a group expense in whole
 * cents. {@code CUSTOM} keeps the requested amounts and requires an exact sum;
 * {@code PERCENTAGE} floors every non-payer share to the cent and gives the
 * remainder to the payer, so no non-payer pays more than its exact share and
 * the sum is always exactly the total. The service validates the user-facing
 * messages before calling these pure helpers.
 */
public final class ExpenseSplit {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ExpenseSplit() {
    }

    /** Shares exactly as requested, in participant order; their sum must equal {@code total}. */
    public static List<BigDecimal> custom(BigDecimal total, List<BigDecimal> amounts) {
        if (total == null || amounts == null || amounts.isEmpty()) {
            throw new IllegalArgumentException("Participantes inválidos para dividir el gasto");
        }
        List<BigDecimal> shares = new ArrayList<>(amounts.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Cada importe personalizado tiene que ser mayor que 0");
            }
            BigDecimal share = amount.setScale(2);
            shares.add(share);
            sum = sum.add(share);
        }
        if (sum.compareTo(total) != 0) {
            throw new IllegalArgumentException("Los importes personalizados deben sumar exactamente el total del gasto");
        }
        return shares;
    }

    /**
     * Percentage shares in participant order. The payer's percentage is part of
     * the 100 sum but not of its amount: the payer gets {@code total - sum(other
     * shares)} and absorbs the rounding remainder.
     */
    public static List<BigDecimal> percentage(BigDecimal total, List<BigDecimal> percentages, int payerIndex) {
        if (total == null || percentages == null || percentages.isEmpty()
                || payerIndex < 0 || payerIndex >= percentages.size()) {
            throw new IllegalArgumentException("Participantes inválidos para dividir el gasto");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal percentage : percentages) {
            if (percentage == null || percentage.signum() <= 0) {
                throw new IllegalArgumentException("Cada porcentaje tiene que ser mayor que 0");
            }
            sum = sum.add(percentage);
        }
        if (sum.compareTo(HUNDRED) != 0) {
            throw new IllegalArgumentException("Los porcentajes deben sumar exactamente 100");
        }

        long totalCents = total.movePointRight(2).longValueExact();
        long nonPayerCents = 0;
        BigDecimal[] shares = new BigDecimal[percentages.size()];
        for (int position = 0; position < percentages.size(); position++) {
            if (position == payerIndex) {
                continue;
            }
            long cents = total.multiply(percentages.get(position))
                    .divide(HUNDRED, 2, RoundingMode.DOWN)
                    .movePointRight(2)
                    .longValueExact();
            shares[position] = BigDecimal.valueOf(cents, 2);
            nonPayerCents += cents;
        }
        shares[payerIndex] = BigDecimal.valueOf(totalCents - nonPayerCents, 2);
        return List.of(shares);
    }
}
