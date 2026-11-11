package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * 022 T064: property tests of the {@code CUSTOM} and {@code PERCENTAGE} split
 * (contracts/expense-split-and-edit.md §1). With a fixed seed, the generated
 * cases prove that the shares always add up to the exact total and that no
 * non-payer share ever exceeds its exact part; the payer absorbs the rounding
 * remainder. No new dependency is used: plain JUnit 5 and {@link Random}.
 */
class GroupExpenseSplitTest {

    private static final long SEED = 20_261_107L;
    private static final int CASES = 500;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static BigDecimal sum(List<BigDecimal> parts) {
        return parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** {@code parts} positive integers that add up to {@code totalUnits}. */
    private static List<BigDecimal> composition(Random random, int parts, long totalUnits, int scale) {
        TreeSet<Long> cuts = new TreeSet<>();
        while (cuts.size() < parts - 1) {
            cuts.add(1 + random.nextLong(totalUnits - 1));
        }
        List<BigDecimal> values = new ArrayList<>(parts);
        long previous = 0;
        for (long cut : cuts) {
            values.add(BigDecimal.valueOf(cut - previous, scale));
            previous = cut;
        }
        values.add(BigDecimal.valueOf(totalUnits - previous, scale));
        return values;
    }

    @Test
    void customSharesKeepTheRequestedAmountsAndSumTheTotal() {
        List<BigDecimal> amounts = List.of(
                new BigDecimal("30.00"), new BigDecimal("60.00"), new BigDecimal("10.00"));

        List<BigDecimal> shares = ExpenseSplit.custom(new BigDecimal("100.00"), amounts);

        assertThat(shares).containsExactlyElementsOf(amounts);
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @Test
    void customRejectsMissingNonPositiveOrNonCanonicalAmounts() {
        BigDecimal total = new BigDecimal("100.00");

        assertThatThrownBy(() -> ExpenseSplit.custom(total,
                java.util.Arrays.asList(new BigDecimal("50.00"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cada importe personalizado tiene que ser mayor que 0");
        assertThatThrownBy(() -> ExpenseSplit.custom(total,
                List.of(new BigDecimal("100.00"), new BigDecimal("0.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cada importe personalizado tiene que ser mayor que 0");
        assertThatThrownBy(() -> ExpenseSplit.custom(total,
                List.of(new BigDecimal("100.00"), new BigDecimal("0.001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cada importe personalizado tiene que ser mayor que 0");
    }

    @Test
    void customRejectsAmountsThatDoNotSumTheTotal() {
        assertThatThrownBy(() -> ExpenseSplit.custom(new BigDecimal("100.00"),
                List.of(new BigDecimal("40.00"), new BigDecimal("59.99"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los importes personalizados deben sumar exactamente el total del gasto");
    }

    @Test
    void percentageSharesAlwaysSumTheTotalAndNoNonPayerExceedsItsExactPart() {
        Random random = new Random(SEED);

        for (int iteration = 0; iteration < CASES; iteration++) {
            int participants = 2 + random.nextInt(5);
            int payerIndex = random.nextInt(participants);
            BigDecimal total = BigDecimal.valueOf(1 + random.nextInt(1_000_000), 2);
            List<BigDecimal> percentages = composition(random, participants, 10_000, 2);

            List<BigDecimal> shares = ExpenseSplit.percentage(total, percentages, payerIndex);

            assertThat(shares).hasSize(participants);
            assertThat(sum(shares))
                    .as("case %d: total %s, percentages %s", iteration, total, percentages)
                    .isEqualByComparingTo(total);

            BigDecimal nonPayerSum = BigDecimal.ZERO;
            for (int position = 0; position < participants; position++) {
                if (position == payerIndex) {
                    continue;
                }
                BigDecimal exact = total.multiply(percentages.get(position)).divide(HUNDRED);
                BigDecimal floored = total.multiply(percentages.get(position))
                        .divide(HUNDRED, 2, RoundingMode.DOWN);
                assertThat(shares.get(position))
                        .as("case %d: non-payer %d of %s", iteration, position, percentages)
                        .isEqualByComparingTo(floored)
                        .isLessThanOrEqualTo(exact);
                nonPayerSum = nonPayerSum.add(shares.get(position));
            }
            assertThat(shares.get(payerIndex)).isEqualByComparingTo(total.subtract(nonPayerSum));
            assertThat(shares.get(payerIndex)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }

    @Test
    void percentagePayerAbsorbsTheRemainder() {
        List<BigDecimal> threeWays =
                List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));

        assertThat(ExpenseSplit.percentage(new BigDecimal("100.00"), threeWays, 2))
                .containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
        assertThat(ExpenseSplit.percentage(new BigDecimal("0.05"), threeWays, 2))
                .containsExactly(new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.03"));
    }

    @Test
    void customSharesAlwaysSumTheTotalForGeneratedAmounts() {
        Random random = new Random(SEED);

        for (int iteration = 0; iteration < CASES; iteration++) {
            int participants = 2 + random.nextInt(5);
            long totalCents = participants + random.nextLong(1_000_000);
            BigDecimal total = BigDecimal.valueOf(totalCents, 2);
            List<BigDecimal> amounts = composition(random, participants, totalCents, 2);

            List<BigDecimal> shares = ExpenseSplit.custom(total, amounts);

            assertThat(shares).containsExactlyElementsOf(amounts);
            assertThat(sum(shares)).isEqualByComparingTo(total);
        }
    }

    @Test
    void percentageRejectsInvalidValuesAndSums() {
        List<BigDecimal> valid = List.of(new BigDecimal("50"), new BigDecimal("50"));

        assertThatThrownBy(() -> ExpenseSplit.percentage(new BigDecimal("10.00"),
                List.of(new BigDecimal("0"), new BigDecimal("100")), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cada porcentaje tiene que ser mayor que 0");
        assertThatThrownBy(() -> ExpenseSplit.percentage(new BigDecimal("10.00"), valid, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpenseSplit.percentage(new BigDecimal("10.00"),
                List.of(new BigDecimal("50"), new BigDecimal("49.99")), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los porcentajes deben sumar exactamente 100");
    }
}
