package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 022 D8: the quote fields are optional and stay null until the conversion is applied. */
class GroupExpenseConversionFieldsTest {

    @Test
    void quoteFieldsAreOptional() {
        GroupExpense expense = new GroupExpense();

        assertThat(expense.getConvertedAmount()).isNull();
        assertThat(expense.getExchangeRate()).isNull();
        assertThat(expense.getRateDate()).isNull();
        assertThat(expense.getRateSource()).isNull();
    }

    @Test
    void quoteFieldsCanBeStored() {
        GroupExpense expense = new GroupExpense();
        expense.setConvertedAmount(new BigDecimal("925.00"));
        expense.setExchangeRate(new BigDecimal("18.50000000"));
        expense.setRateDate(LocalDate.of(2026, 1, 15));
        expense.setRateSource("BCRA");

        assertThat(expense.getConvertedAmount()).isEqualByComparingTo("925.00");
        assertThat(expense.getExchangeRate()).isEqualByComparingTo("18.50000000");
        assertThat(expense.getRateDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(expense.getRateSource()).isEqualTo("BCRA");
    }
}
