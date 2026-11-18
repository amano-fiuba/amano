package com.amano.backend.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amano.backend.common.CurrencyDateTotal;
import com.amano.backend.expense.ExpenseRepository;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.income.IncomePeriod;
import com.amano.backend.income.IncomeRepository;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/** Unit tests of the personal balance (specs/033-income-management, FR-010 to FR-012). */
class BalanceServiceTest {

    private static final Long USER_ID = 3L;
    private static final IncomePeriod PERIOD = IncomePeriod.of(null, null, null, null);
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDate PAST = TODAY.minusDays(5);
    private static final LocalDate FUTURE = TODAY.plusDays(10);

    private IncomeRepository incomes;
    private ExpenseRepository expenses;
    private UserRepository users;
    private ExchangeRateProvider rates;
    private BalanceService service;

    @BeforeEach
    void setUp() {
        incomes = mock(IncomeRepository.class);
        expenses = mock(ExpenseRepository.class);
        users = mock(UserRepository.class);
        rates = mock(ExchangeRateProvider.class);
        service = new BalanceService(incomes, expenses, users, rates);
        preferences(Set.of());
        when(incomes.sumByCurrencyAndDate(any(), any(), any())).thenReturn(List.of());
        when(expenses.sumByCurrencyAndDate(any(), any(), any())).thenReturn(List.of());
        // The real provider answers 1 for the same currency.
        when(rates.getQuote(eq(Currency.ARS), eq(Currency.ARS), any()))
                .thenReturn(quote("1"));
    }

    private void preferences(Set<Currency> currencies) {
        User user = mock(User.class);
        when(user.getCurrencies()).thenReturn(currencies);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private static ExchangeRateQuote quote(String rate) {
        return new ExchangeRateQuote(new BigDecimal(rate), TODAY, "TEST");
    }

    private static CurrencyDateTotal total(Currency currency, LocalDate date, String amount) {
        return new CurrencyDateTotal(currency, date, new BigDecimal(amount));
    }

    @Test
    void theNetBalanceIsIncomeMinusExpenses() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, PAST, "1000.00")));
        when(expenses.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, PAST, "400.00")));

        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.currency()).isEqualTo("ARS");
        assertThat(balance.income()).isEqualTo("1000.00");
        assertThat(balance.expenses()).isEqualTo("400.00");
        assertThat(balance.net()).isEqualTo("600.00");
        assertThat(balance.projectedIncome()).isEqualTo("0.00");
    }

    @Test
    void theNetBalanceIsNegativeWhenExpensesExceedIncome() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, PAST, "100.00")));
        when(expenses.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, PAST, "350.50")));

        assertThat(service.balance(USER_ID, PERIOD).net()).isEqualTo("-250.50");
    }

    @Test
    void withoutIncomesTheNetIsMinusTheExpensesAndWithoutExpensesItIsTheIncome() {
        when(expenses.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, PAST, "75.00")));
        assertThat(service.balance(USER_ID, PERIOD).net()).isEqualTo("-75.00");

        when(expenses.sumByCurrencyAndDate(any(), any(), any())).thenReturn(List.of());
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, PAST, "20.00")));
        assertThat(service.balance(USER_ID, PERIOD).net()).isEqualTo("20.00");
    }

    @Test
    void anEmptyPeriodHasAZeroBalance() {
        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.income()).isEqualTo("0.00");
        assertThat(balance.expenses()).isEqualTo("0.00");
        assertThat(balance.net()).isEqualTo("0.00");
    }

    @Test
    void convertsEachGroupWithTheQuoteOfItsOwnDate() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, PAST, "100.00")));
        when(expenses.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, PAST.minusDays(1), "10.00")));
        when(rates.getQuote(Currency.USD, Currency.ARS, PAST)).thenReturn(quote("1000"));
        when(rates.getQuote(Currency.USD, Currency.ARS, PAST.minusDays(1)))
                .thenReturn(quote("900"));

        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.income()).isEqualTo("100000.00");
        assertThat(balance.expenses()).isEqualTo("9000.00");
        assertThat(balance.net()).isEqualTo("91000.00");
    }

    @Test
    void roundsEachConvertedGroupHalfUpToTwoDecimals() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, PAST, "1.00")));
        when(rates.getQuote(Currency.USD, Currency.ARS, PAST)).thenReturn(quote("0.005"));

        assertThat(service.balance(USER_ID, PERIOD).income()).isEqualTo("0.01");
    }

    @Test
    void asksForOneQuotePerCurrencyAndDateNotPerMovement() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, PAST, "100.00")));
        when(expenses.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, PAST, "10.00")));
        when(rates.getQuote(Currency.USD, Currency.ARS, PAST)).thenReturn(quote("1000"));

        service.balance(USER_ID, PERIOD);

        verify(rates, times(2)).getQuote(Currency.USD, Currency.ARS, PAST);
    }

    @Test
    void scheduledIncomesAreInformativeAndNeverEnterTheNet() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any())).thenReturn(List.of(
                total(Currency.ARS, PAST, "1000.00"), total(Currency.ARS, FUTURE, "500.00")));

        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.income()).isEqualTo("1000.00");
        assertThat(balance.projectedIncome()).isEqualTo("500.00");
        assertThat(balance.net()).isEqualTo("1000.00");
    }

    @Test
    void anIncomeDatedTodayIsAlreadyReceived() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, TODAY, "300.00")));

        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.income()).isEqualTo("300.00");
        assertThat(balance.projectedIncome()).isEqualTo("0.00");
    }

    @Test
    void aScheduledForeignIncomeFallsBackToTodaysQuoteWhenItsDateHasNone() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, FUTURE, "10.00")));
        when(rates.getQuote(Currency.USD, Currency.ARS, FUTURE))
                .thenThrow(new ExchangeRateUnavailableException());
        when(rates.getQuote(Currency.USD, Currency.ARS, TODAY)).thenReturn(quote("1200"));

        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.projectedIncome()).isEqualTo("12000.00");
        assertThat(balance.net()).isEqualTo("0.00");
    }

    @Test
    void aScheduledForeignIncomeWithoutAnyQuoteLeavesProjectedOutWithoutFailing() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any())).thenReturn(List.of(
                total(Currency.ARS, PAST, "1000.00"), total(Currency.USD, FUTURE, "10.00")));
        when(rates.getQuote(eq(Currency.USD), eq(Currency.ARS), any()))
                .thenThrow(new ExchangeRateUnavailableException());

        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.projectedIncome()).isNull();
        assertThat(balance.net()).isEqualTo("1000.00");
    }

    @Test
    void aMissingQuoteForAnExpenseFailsTheWholeBalance() {
        when(expenses.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, PAST, "10.00")));
        when(rates.getQuote(Currency.USD, Currency.ARS, PAST))
                .thenThrow(new ExchangeRateUnavailableException());

        assertThatThrownBy(() -> service.balance(USER_ID, PERIOD))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void aMissingQuoteForAReceivedIncomeFailsTheWholeBalance() {
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.USD, PAST, "10.00")));
        when(rates.getQuote(Currency.USD, Currency.ARS, PAST)).thenReturn(null);

        assertThatThrownBy(() -> service.balance(USER_ID, PERIOD))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void usesTheMainCurrencyOfTheAccountAsTheBalanceCurrency() {
        preferences(Set.of(Currency.USD));
        when(incomes.sumByCurrencyAndDate(any(), any(), any()))
                .thenReturn(List.of(total(Currency.ARS, PAST, "1000.00")));
        when(rates.getQuote(Currency.ARS, Currency.USD, PAST)).thenReturn(quote("0.001"));

        BalanceResponse balance = service.balance(USER_ID, PERIOD);

        assertThat(balance.currency()).isEqualTo("USD");
        assertThat(balance.income()).isEqualTo("1.00");
    }

    @Test
    void anUnknownAccountFallsBackToArs() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.balance(USER_ID, PERIOD).currency()).isEqualTo("ARS");
    }

    @Test
    void theMainCurrencyFollowsTheArsUsdEurOrderThenTheFirstOther() {
        assertThat(BalanceService.mainCurrency(null)).isEqualTo(Currency.ARS);
        assertThat(BalanceService.mainCurrency(Set.of())).isEqualTo(Currency.ARS);
        assertThat(BalanceService.mainCurrency(Set.of(Currency.USD, Currency.ARS)))
                .isEqualTo(Currency.ARS);
        assertThat(BalanceService.mainCurrency(Set.of(Currency.EUR, Currency.USD)))
                .isEqualTo(Currency.USD);
        assertThat(BalanceService.mainCurrency(Set.of(Currency.BRL, Currency.EUR)))
                .isEqualTo(Currency.EUR);
        assertThat(BalanceService.mainCurrency(Set.of(Currency.BRL))).isEqualTo(Currency.BRL);
        assertThat(BalanceService.mainCurrency(Set.of(Currency.CLP, Currency.BRL)))
                .isEqualTo(Currency.BRL);
    }
}
