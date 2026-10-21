package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.Currency;

/**
 * Canonical amount parsing (FR-013/FR-014/FR-022/FR-023, SC-004). Currency does
 * not affect the numeric value: the same canonical amount is valid for ARS, USD
 * and EUR.
 */
class ExpenseAmountNormalizerTest {

    /** SC-004 battery expressed as the canonical form the API actually receives. */
    private static final String[] SC_004_CANONICAL = {
            "0.01", "0.10", "0.99", "1.00", "1.10", "10.10",
            "100.01", "1234.56", "999999.99", "123456789.01",
            "999999999.98", "999999999.99"
    };

    @Test
    void acceptsTheExactSc004BatteryForEveryCurrency() {
        for (Currency currency : Currency.values()) {
            for (String canonical : SC_004_CANONICAL) {
                BigDecimal value = ExpenseAmountNormalizer.normalize(canonical);
                assertThat(value.toPlainString())
                        .as("%s in %s", canonical, currency)
                        .isEqualTo(canonical);
            }
        }
    }

    @Test
    void acceptsTheRangeBoundaries() {
        assertThat(ExpenseAmountNormalizer.normalize("0.01"))
                .isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(ExpenseAmountNormalizer.normalize("999999999.99"))
                .isEqualByComparingTo(new BigDecimal("999999999.99"));
    }

    @Test
    void normalizesToFixedScaleTwo() {
        BigDecimal oneDecimal = ExpenseAmountNormalizer.normalize("10.1");
        BigDecimal twoDecimals = ExpenseAmountNormalizer.normalize("10.10");

        assertThat(oneDecimal).isEqualByComparingTo(twoDecimals);
        assertThat(oneDecimal.scale()).isEqualTo(2);
        assertThat(oneDecimal.toPlainString()).isEqualTo("10.10");
        assertThat(twoDecimals.toPlainString()).isEqualTo("10.10");
    }

    @Test
    void rejectsMissingOrBlankAmounts() {
        assertThat(ExpenseAmountNormalizer.validationError(null))
                .isEqualTo(ExpenseAmountNormalizer.REQUIRED_MESSAGE);
        assertThat(ExpenseAmountNormalizer.validationError(""))
                .isEqualTo(ExpenseAmountNormalizer.REQUIRED_MESSAGE);
        assertThat(ExpenseAmountNormalizer.validationError("   "))
                .isEqualTo(ExpenseAmountNormalizer.REQUIRED_MESSAGE);
    }

    @Test
    void rejectsNonCanonicalNotation() {
        for (String raw : new String[] {
                "abc", "1,5", "1.234,56", "-1.00", "+1.00", "1e3", "1E+2", ".5", "5.",
                "1 0.10", "\u00A010.10", "\uFEFF10.10" }) {
            assertThat(ExpenseAmountNormalizer.validationError(raw))
                    .as("raw=%s", raw)
                    .isEqualTo(ExpenseAmountNormalizer.FORMAT_MESSAGE);
        }
    }

    @Test
    void rejectsMoreThanTwoDecimalsWithoutRounding() {
        for (String raw : new String[] { "10.105", "0.001", "999999999.999" }) {
            assertThat(ExpenseAmountNormalizer.validationError(raw))
                    .as("raw=%s", raw)
                    .isEqualTo(ExpenseAmountNormalizer.DECIMALS_MESSAGE);
        }
    }

    @Test
    void rejectsZeroAndNegativeValues() {
        for (String raw : new String[] { "0", "0.00" }) {
            assertThat(ExpenseAmountNormalizer.validationError(raw))
                    .as("raw=%s", raw)
                    .isEqualTo(ExpenseAmountNormalizer.POSITIVE_MESSAGE);
        }
    }

    @Test
    void rejectsValuesOutsideTheRange() {
        assertThat(ExpenseAmountNormalizer.validationError("1000000000.00"))
                .isEqualTo(ExpenseAmountNormalizer.RANGE_MESSAGE);
    }

    @Test
    void normalizeThrowsTheFieldErrorWhenInvalid() {
        assertThatThrownBy(() -> ExpenseAmountNormalizer.normalize("abc"))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(exception -> assertThat(
                        ((FieldValidationException) exception).getFields())
                        .containsEntry("amount", ExpenseAmountNormalizer.FORMAT_MESSAGE));
    }
}
