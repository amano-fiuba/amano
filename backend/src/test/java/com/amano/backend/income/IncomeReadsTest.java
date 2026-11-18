package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/** Reads of the income service: the detail and the conversions to the preferred currencies. */
class IncomeReadsTest {

    private static final Long USER_ID = 9L;
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private IncomeRepository repository;
    private UserRepository users;
    private ExchangeRateProvider rates;
    private IncomeService service;

    @BeforeEach
    void setUp() {
        repository = mock(IncomeRepository.class);
        users = mock(UserRepository.class);
        rates = mock(ExchangeRateProvider.class);
        service = new IncomeService(repository, users, rates);
        preferences(Set.of());
    }

    private void preferences(Set<Currency> currencies) {
        User user = mock(User.class);
        when(user.getCurrencies()).thenReturn(currencies);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private static Income income(Currency currency, String amount, LocalDate date) {
        Income income = new Income();
        income.setUserId(USER_ID);
        income.setAmount(new BigDecimal(amount));
        income.setCurrency(currency);
        income.setIncomeDate(date);
        income.setCategory(IncomeCategory.FREELANCE);
        income.setPaymentMethod(IncomePaymentMethod.TRANSFER);
        return income;
    }

    private static ExchangeRateQuote quote(String rate) {
        return new ExchangeRateQuote(new BigDecimal(rate), DATE, "TEST");
    }

    @Test
    void theDetailOfAnOwnIncomeCarriesItsConversions() {
        Income income = income(Currency.USD, "100.00", DATE);
        when(repository.findByIdAndUserId(4L, USER_ID)).thenReturn(Optional.of(income));
        when(rates.getQuote(Currency.USD, Currency.ARS, DATE)).thenReturn(quote("1000"));

        IncomeResponse response = service.getIncome(USER_ID, 4L);

        assertThat(response.amount()).isEqualTo("100.00");
        assertThat(response.conversions()).containsExactly(
                new IncomeResponse.Conversion("ARS", "100000.00"));
    }

    @Test
    void aMissingOrForeignIncomeIsTheSameNotFound() {
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getIncome(USER_ID, 77L))
                .isInstanceOf(IncomeNotFoundException.class)
                .hasMessage("No encontramos ese ingreso");
    }

    @Test
    void convertsToEveryPreferredCurrencyExceptItsOwnInCatalogOrder() {
        preferences(Set.of(Currency.EUR, Currency.USD, Currency.ARS));
        when(rates.getQuote(Currency.ARS, Currency.USD, DATE)).thenReturn(quote("0.001"));
        when(rates.getQuote(Currency.ARS, Currency.EUR, DATE)).thenReturn(quote("0.0009"));

        List<IncomeResponse> responses = service.withConversions(USER_ID,
                List.of(income(Currency.ARS, "1000.00", DATE)));

        assertThat(responses.get(0).conversions()).containsExactly(
                new IncomeResponse.Conversion("USD", "1.00"),
                new IncomeResponse.Conversion("EUR", "0.90"));
    }

    @Test
    void anAccountWithoutPreferencesConvertsToArsAndSkipsAnArsIncome() {
        when(rates.getQuote(Currency.USD, Currency.ARS, DATE)).thenReturn(quote("1000"));

        List<IncomeResponse> responses = service.withConversions(USER_ID, List.of(
                income(Currency.USD, "2.00", DATE), income(Currency.ARS, "5.00", DATE)));

        assertThat(responses.get(0).conversions()).hasSize(1);
        assertThat(responses.get(1).conversions()).isEmpty();
    }

    @Test
    void aMissingQuoteOnlyOmitsThatConversion() {
        preferences(Set.of(Currency.ARS, Currency.EUR));
        when(rates.getQuote(Currency.USD, Currency.ARS, DATE))
                .thenThrow(new ExchangeRateUnavailableException());
        when(rates.getQuote(Currency.USD, Currency.EUR, DATE)).thenReturn(quote("0.9"));

        List<IncomeResponse> responses = service.withConversions(USER_ID,
                List.of(income(Currency.USD, "10.00", DATE)));

        assertThat(responses.get(0).conversions()).containsExactly(
                new IncomeResponse.Conversion("EUR", "9.00"));
    }

    @Test
    void aQuoteWithoutRateIsSkipped() {
        when(rates.getQuote(Currency.USD, Currency.ARS, DATE))
                .thenReturn(new ExchangeRateQuote(null, DATE, "TEST"));

        assertThat(service.withConversions(USER_ID, List.of(income(Currency.USD, "1.00", DATE)))
                .get(0).conversions()).isEmpty();
    }

    @Test
    void withoutCollaboratorsTheConversionsAreEmpty() {
        IncomeService bare = new IncomeService(repository);

        assertThat(bare.withConversions(USER_ID, List.of(income(Currency.USD, "1.00", DATE)))
                .get(0).conversions()).isEmpty();
    }

    @Test
    void anUnknownAccountDefaultsToArs() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        when(rates.getQuote(Currency.USD, Currency.ARS, DATE)).thenReturn(quote("2"));

        assertThat(service.withConversions(USER_ID, List.of(income(Currency.USD, "3.00", DATE)))
                .get(0).conversions()).containsExactly(new IncomeResponse.Conversion("ARS", "6.00"));
    }
}
