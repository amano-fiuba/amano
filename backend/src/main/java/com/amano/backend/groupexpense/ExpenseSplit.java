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

    /**
     * {@code CUSTOM} shares. The {@code originalAmounts} are validated against
     * {@code originalTotal} (their sum must match it exactly). The converted
     * total is then distributed: every non-payer receives
     * floor(convertedTotal * amount_i / originalTotal) in cents and the payer
     * absorbs the remainder. When both totals are equal the parts are exactly
     * the entered amounts.
     */
    public static List<BigDecimal> custom(BigDecimal convertedTotal, List<BigDecimal> originalAmounts,
            BigDecimal originalTotal, int payerIndex) {
        if (convertedTotal == null || originalAmounts == null || originalAmounts.isEmpty()
                || originalTotal == null || originalTotal.signum() <= 0
                || payerIndex < 0 || payerIndex >= originalAmounts.size()) {
            throw new IllegalArgumentException("Participantes inválidos para dividir el gasto");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amount : originalAmounts) {
            if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Cada importe personalizado tiene que ser mayor que 0");
            }
            sum = sum.add(amount);
        }
        if (sum.compareTo(originalTotal) != 0) {
            throw new IllegalArgumentException("Los importes personalizados deben sumar exactamente el total del gasto");
        }

        long convertedCents = convertedTotal.movePointRight(2).longValueExact();
        long nonPayerCents = 0;
        BigDecimal[] shares = new BigDecimal[originalAmounts.size()];
        for (int position = 0; position < originalAmounts.size(); position++) {
            if (position == payerIndex) {
                continue;
            }
            BigDecimal exact = convertedTotal.multiply(originalAmounts.get(position))
                    .divide(originalTotal, 10, RoundingMode.DOWN);
            long cents = exact.movePointRight(2).longValue();
            shares[position] = BigDecimal.valueOf(cents, 2);
            nonPayerCents += cents;
        }
        shares[payerIndex] = BigDecimal.valueOf(convertedCents - nonPayerCents, 2);
        return List.of(shares);
    }

    /** Backwards-compatible helper for the same-currency case. */
    public static List<BigDecimal> custom(BigDecimal total, List<BigDecimal> amounts) {
        return custom(total, amounts, total, 0);
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
