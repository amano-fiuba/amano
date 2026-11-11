package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Feature: personal activity screen shows the equivalent of each expense in the
 * user's preferred currencies (ARS when none is configured), skipping the
 * expense's own currency and omitting a target when its quote is unavailable.
 */
class ExpenseHistoryConversionsTest {

    private static final Long USER_ID = 7L;

    private ExpenseRepository expenseRepository;
    private UserRepository userRepository;
    private ExchangeRateProvider exchangeRateProvider;
    private ExpenseService service;

    @BeforeEach
    void setUp() {
        expenseRepository = Mockito.mock(ExpenseRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        exchangeRateProvider = Mockito.mock(ExchangeRateProvider.class);
        service = new ExpenseService(expenseRepository, userRepository, exchangeRateProvider);
    }

    private static Expense expense(long id, String amount, Currency currency, LocalDate date) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setUserId(USER_ID);
        expense.setAmount(new BigDecimal(amount));
        expense.setCurrency(currency);
        expense.setExpenseDate(date);
        expense.setCategory(ExpenseCategory.FOOD);
        expense.setType(ExpenseType.ONE_TIME);
        expense.setPaymentMethod(PaymentMethod.CASH);
        return expense;
    }

    private void preferred(Currency... currencies) {
        User user = new User();
        user.getCurrencies().addAll(Set.of(currencies));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void quote(Currency from, Currency to, String rate) {
        when(exchangeRateProvider.getQuote(from, to, LocalDate.parse("2026-11-02")))
                .thenReturn(new ExchangeRateQuote(new BigDecimal(rate),
                        LocalDate.parse("2026-11-02"), "BCRA"));
    }

    @Test
    void convertsToEveryPreferredCurrencyExceptTheExpenseOne() {
        preferred(Currency.ARS, Currency.USD);
        Expense usd = expense(1L, "10.00", Currency.USD, LocalDate.parse("2026-11-02"));
        quote(Currency.USD, Currency.ARS, "1000.00");
        when(expenseRepository.findTop200ByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(usd));

        List<ExpenseResponse> history = service.listHistory(USER_ID);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).conversions())
                .containsExactly(new ExpenseResponse.Conversion("ARS", "10000.00"));
    }

    @Test
    void defaultsToArsWhenNoPreferredCurrencyIsConfigured() {
        User user = new User();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        Expense usd = expense(1L, "10.00", Currency.USD, LocalDate.parse("2026-11-02"));
        quote(Currency.USD, Currency.ARS, "1000.00");
        when(expenseRepository.findTop200ByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(usd));

        assertThat(service.listHistory(USER_ID).get(0).conversions())
                .containsExactly(new ExpenseResponse.Conversion("ARS", "10000.00"));
    }

    @Test
    void omitsTheConversionToTheSameCurrencyAndSurvivesAnUnavailableQuote() {
        preferred(Currency.ARS, Currency.EUR);
        Expense ars = expense(1L, "1500.00", Currency.ARS, LocalDate.parse("2026-11-02"));
        Expense usd = expense(2L, "10.00", Currency.USD, LocalDate.parse("2026-11-02"));
        when(exchangeRateProvider.getQuote(Currency.USD, Currency.EUR, LocalDate.parse("2026-11-02")))
                .thenThrow(new ExchangeRateUnavailableException());
        quote(Currency.ARS, Currency.EUR, "0.0009");
        when(expenseRepository.findTop200ByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(usd, ars));

        List<ExpenseResponse> history = service.listHistory(USER_ID);

        assertThat(history.get(0).conversions()).isEmpty();
        // ARS to ARS is skipped; only the EUR equivalent is attached.
        assertThat(history.get(1).conversions())
                .containsExactly(new ExpenseResponse.Conversion("EUR", "1.35"));
    }
}
