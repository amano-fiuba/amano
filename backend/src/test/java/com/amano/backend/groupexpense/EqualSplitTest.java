package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 017 T007: equal division in whole cents (research.md D4, FR-005/FR-006,
 * SC-001/SC-002). The payer absorbs the remainder when participating; otherwise
 * the first participants of the request get one extra cent each.
 */
class EqualSplitTest {

    private static List<String> split(String total, int participants, int payerIndex) {
        return EqualSplit.split(new BigDecimal(total), participants, payerIndex).stream()
                .map(BigDecimal::toPlainString)
                .toList();
    }

    private static BigDecimal sum(List<BigDecimal> parts) {
        return parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void anExactDivisionGivesTheSameShareToEveryone() {
        assertThat(split("100.00", 2, 0)).containsExactly("50.00", "50.00");
        assertThat(split("90.00", 3, -1)).containsExactly("30.00", "30.00", "30.00");
    }

    @Test
    void thePayerAbsorbsTheRemainderWhenParticipating() {
        assertThat(split("10000.00", 3, 0)).containsExactly("3333.34", "3333.33", "3333.33");
        assertThat(split("10000.00", 3, 2)).containsExactly("3333.33", "3333.33", "3333.34");
        assertThat(split("0.05", 3, 1)).containsExactly("0.01", "0.03", "0.01");
    }

    @Test
    void withoutThePayerTheFirstParticipantsGetOneCentEach() {
        assertThat(split("100.00", 3, -1)).containsExactly("33.34", "33.33", "33.33");
        assertThat(split("0.05", 3, -1)).containsExactly("0.02", "0.02", "0.01");
    }

    @Test
    void noDebtorPaysMoreThanTheExactShareWhenThePayerParticipates() {
        List<BigDecimal> parts = EqualSplit.split(new BigDecimal("100.00"), 7, 0);
        BigDecimal exact = new BigDecimal("100.00").divide(BigDecimal.valueOf(7), 10, java.math.RoundingMode.HALF_UP);

        for (BigDecimal debtorShare : parts.subList(1, parts.size())) {
            assertThat(debtorShare).isLessThanOrEqualTo(exact);
        }
    }

    @Test
    void sharesNeverDifferFromTheExactDivisionByMoreThanOneCentForDebtors() {
        List<BigDecimal> parts = EqualSplit.split(new BigDecimal("100.00"), 3, -1);
        BigDecimal exact = new BigDecimal("100.00").divide(BigDecimal.valueOf(3), 10, java.math.RoundingMode.HALF_UP);

        assertThat(parts).allSatisfy(part ->
                assertThat(part.subtract(exact).abs()).isLessThanOrEqualTo(new BigDecimal("0.01")));
    }

    @Test
    void theSumIsAlwaysExactlyTheTotal() {
        String[] totals = {"0.03", "0.10", "1.00", "10.01", "999.99", "123456.78", "999999999.99"};
        for (String total : totals) {
            for (int participants = 1; participants <= 9; participants++) {
                if (new BigDecimal(total).movePointRight(2).longValueExact() < participants) {
                    continue;
                }
                assertThat(sum(EqualSplit.split(new BigDecimal(total), participants, 0)))
                        .isEqualByComparingTo(total);
                assertThat(sum(EqualSplit.split(new BigDecimal(total), participants, -1)))
                        .isEqualByComparingTo(total);
            }
        }
    }

    @Test
    void theMaximumAmountSplitsWithoutLosingCents() {
        List<BigDecimal> parts = EqualSplit.split(new BigDecimal("999999999.99"), 7, -1);

        assertThat(parts).hasSize(7);
        assertThat(sum(parts)).isEqualByComparingTo("999999999.99");
        assertThat(parts.get(0).toPlainString()).isEqualTo("142857142.86");
        assertThat(parts.get(6).toPlainString()).isEqualTo("142857142.85");
    }

    @Test
    void everyShareHasTwoDecimals() {
        assertThat(EqualSplit.split(new BigDecimal("10"), 4, 0))
                .allSatisfy(part -> assertThat(part.scale()).isEqualTo(2));
    }

    @Test
    void aTotalBelowOneCentPerParticipantIsRejected() {
        assertThat(EqualSplit.fits(new BigDecimal("0.02"), 3)).isFalse();
        assertThat(EqualSplit.fits(new BigDecimal("0.03"), 3)).isTrue();
        assertThatThrownBy(() -> EqualSplit.split(new BigDecimal("0.02"), 3, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> EqualSplit.split(new BigDecimal("10.00"), 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EqualSplit.split(new BigDecimal("10.00"), 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EqualSplit.split(null, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EqualSplit.split(new BigDecimal("10.001"), 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(EqualSplit.fits(null, 2)).isFalse();
        assertThat(EqualSplit.fits(new BigDecimal("10.00"), 0)).isFalse();
    }
}
