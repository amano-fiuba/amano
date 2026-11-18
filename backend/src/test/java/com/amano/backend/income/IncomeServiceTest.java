package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.user.Currency;

/** Unit tests of the personal income registration (specs/032-income-management, US1). */
class IncomeServiceTest {

    private static final Long USER_ID = 7L;

    private IncomeRepository repository;
    private IncomeService service;
    private final UUID key = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(IncomeRepository.class);
        when(repository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Income.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new IncomeService(repository);
    }

    private static RegisterIncomeRequest request(Consumer<RegisterIncomeRequest> customizer) {
        RegisterIncomeRequest request = new RegisterIncomeRequest();
        request.setAmount("850000.00");
        request.setCurrency("ARS");
        request.setDate("2026-03-10");
        request.setCategory("SALARY");
        request.setPaymentMethod("TRANSFER");
        request.setDescription("Sueldo marzo");
        request.setDeviceTimeZone("UTC");
        customizer.accept(request);
        return request;
    }

    private static RegisterIncomeRequest valid() {
        return request(r -> { });
    }

    private FieldValidationException rejected(RegisterIncomeRequest request) {
        assertThatThrownBy(() -> service.registerIncome(USER_ID, key, request))
                .isInstanceOf(FieldValidationException.class);
        verify(repository, never()).save(any());
        try {
            service.registerIncome(USER_ID, key, request);
        }
        catch (FieldValidationException expected) {
            return expected;
        }
        throw new AssertionError("expected a validation error");
    }

    @Test
    void registersTheIncomeForTheSessionUserWithTheNormalizedValues() {
        IncomeService.Registration registration = service.registerIncome(USER_ID, key, valid());

        ArgumentCaptor<Income> saved = ArgumentCaptor.forClass(Income.class);
        verify(repository).save(saved.capture());
        Income income = saved.getValue();
        assertThat(income.getUserId()).isEqualTo(USER_ID);
        assertThat(income.getAmount()).isEqualByComparingTo(new BigDecimal("850000.00"));
        assertThat(income.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(income.getIncomeDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(income.getCategory()).isEqualTo(IncomeCategory.SALARY);
        assertThat(income.getPaymentMethod()).isEqualTo(IncomePaymentMethod.TRANSFER);
        assertThat(income.getDescription()).isEqualTo("Sueldo marzo");
        assertThat(income.getIdempotencyKey()).isEqualTo(key);
        assertThat(registration.created()).isTrue();
        assertThat(registration.income().amount()).isEqualTo("850000.00");
        assertThat(registration.income().projected()).isFalse();
    }

    @Test
    void aBlankDescriptionIsStoredAsAbsent() {
        service.registerIncome(USER_ID, key, request(r -> r.setDescription("   \n ")));

        ArgumentCaptor<Income> saved = ArgumentCaptor.forClass(Income.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isNull();
    }

    @Test
    void theDescriptionIsOptional() {
        service.registerIncome(USER_ID, key, request(r -> r.setDescription(null)));

        verify(repository).save(any(Income.class));
    }

    @Test
    void rejectsAnAmountThatIsZeroNegativeOrWithMoreThanTwoDecimals() {
        for (String amount : new String[] { "0", "0.00", "-5", "1.234", "abc", "", null,
                "1000000000.00" }) {
            FieldValidationException error = rejected(request(r -> r.setAmount(amount)));
            assertThat(error.getFields()).containsOnlyKeys("amount");
        }
    }

    @Test
    void rejectsAMissingOrUnknownCurrency() {
        assertThat(rejected(request(r -> r.setCurrency(null))).getFields())
                .containsEntry("currency", IncomeService.CURRENCY_REQUIRED);
        assertThat(rejected(request(r -> r.setCurrency("XYZ"))).getFields())
                .containsEntry("currency", IncomeService.CURRENCY_INVALID);
    }

    @Test
    void acceptsAnyOfTheSupportedCurrencies() {
        service.registerIncome(USER_ID, key, request(r -> r.setCurrency("BRL")));

        ArgumentCaptor<Income> saved = ArgumentCaptor.forClass(Income.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCurrency()).isEqualTo(Currency.BRL);
    }

    @Test
    void rejectsAMissingOrMalformedDate() {
        assertThat(rejected(request(r -> r.setDate(null))).getFields())
                .containsEntry("date", "La fecha es obligatoria");
        assertThat(rejected(request(r -> r.setDate("10/03/2026"))).getFields())
                .containsEntry("date", IncomeService.DATE_FORMAT);
        assertThat(rejected(request(r -> r.setDate("2026-02-31"))).getFields())
                .containsEntry("date", IncomeService.DATE_FORMAT);
    }

    @Test
    void acceptsAFutureDateUpToOneYearAheadAndMarksItProjected() {
        LocalDate limit = LocalDate.now(ZoneOffset.UTC).plusYears(1);

        IncomeService.Registration registration = service.registerIncome(USER_ID, key,
                request(r -> r.setDate(limit.toString())));

        assertThat(registration.income().projected()).isTrue();
        assertThat(registration.income().date()).isEqualTo(limit.toString());
    }

    @Test
    void rejectsADateMoreThanOneYearAhead() {
        LocalDate tooFar = LocalDate.now(ZoneOffset.UTC).plusYears(1).plusDays(1);

        FieldValidationException error = rejected(request(r -> r.setDate(tooFar.toString())));

        assertThat(error.getFields()).containsEntry("date", IncomeService.DATE_TOO_FAR);
    }

    @Test
    void anInvalidTimeZoneFallsBackToUtcForTheLimit() {
        LocalDate limit = LocalDate.now(ZoneOffset.UTC).plusYears(1);

        service.registerIncome(USER_ID, key, request(r -> {
            r.setDate(limit.toString());
            r.setDeviceTimeZone("No/Such_Zone");
        }));
        service.registerIncome(USER_ID, UUID.randomUUID(), request(r -> {
            r.setDate(limit.toString());
            r.setDeviceTimeZone(null);
        }));

        verify(repository, org.mockito.Mockito.times(2)).save(any(Income.class));
    }

    @Test
    void rejectsACategoryOrPaymentMethodOutsideTheirCatalogs() {
        assertThat(rejected(request(r -> r.setCategory("FOOD"))).getFields())
                .containsEntry("category", IncomeService.CATEGORY_INVALID);
        assertThat(rejected(request(r -> r.setCategory(null))).getFields())
                .containsEntry("category", IncomeService.CATEGORY_REQUIRED);
        assertThat(rejected(request(r -> r.setPaymentMethod("CREDIT_CARD"))).getFields())
                .containsEntry("paymentMethod", IncomeService.PAYMENT_INVALID);
        assertThat(rejected(request(r -> r.setPaymentMethod(" "))).getFields())
                .containsEntry("paymentMethod", IncomeService.PAYMENT_REQUIRED);
    }

    @Test
    void rejectsADescriptionOfMoreThanTwoHundredCharacters() {
        FieldValidationException error = rejected(request(r -> r.setDescription("a".repeat(201))));

        assertThat(error.getFields()).containsOnlyKeys("description");
    }

    @Test
    void reportsEveryInvalidFieldInOnePass() {
        FieldValidationException error = rejected(request(r -> {
            r.setAmount("0");
            r.setCurrency(null);
            r.setDate(null);
            r.setCategory(null);
            r.setPaymentMethod(null);
        }));

        assertThat(error.getFields()).containsOnlyKeys(
                "amount", "currency", "date", "category", "paymentMethod");
    }

    @Test
    void rejectsReservedPropertiesThatCouldSelectTheOwner() {
        RegisterIncomeRequest request = valid();
        request.putUnknownProperty("userId", 99);
        request.putUnknownProperty("harmless", "x");

        FieldValidationException error = rejected(request);

        assertThat(error.getFields()).containsOnlyKeys("userId")
                .containsEntry("userId", IncomeService.RESERVED_PROPERTY);
    }

    @Test
    void replaysTheExistingIncomeWhenTheSameKeyArrivesWithTheSamePayload() {
        Income existing = incomeOf("850000.00", "Sueldo marzo");
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, key))
                .thenReturn(Optional.of(existing));

        IncomeService.Registration registration = service.registerIncome(USER_ID, key, valid());

        assertThat(registration.created()).isFalse();
        assertThat(registration.income().amount()).isEqualTo("850000.00");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsTheSameKeyWithADifferentPayload() {
        Income existing = incomeOf("1.00", "Otro");
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, key))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerIncome(USER_ID, key, valid()))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void resolvesAConcurrentRegistrationByRereadingTheWinner() {
        Income winner = incomeOf("850000.00", "Sueldo marzo");
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, key))
                .thenReturn(Optional.of(winner));

        assertThat(service.resolveConcurrentRegistration(USER_ID, key, valid()).created())
                .isFalse();
        assertThatThrownBy(() -> service.resolveConcurrentRegistration(USER_ID, key,
                request(r -> r.setAmount("5.00"))))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aConflictWithoutAPersistedWinnerIsNotSwallowed() {
        assertThatThrownBy(() -> service.resolveConcurrentRegistration(USER_ID, key, valid()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Income incomeOf(String amount, String description) {
        Income income = new Income();
        income.setUserId(USER_ID);
        income.setAmount(new BigDecimal(amount));
        income.setCurrency(Currency.ARS);
        income.setIncomeDate(LocalDate.of(2026, 3, 10));
        income.setCategory(IncomeCategory.SALARY);
        income.setPaymentMethod(IncomePaymentMethod.TRANSFER);
        income.setDescription(description);
        return income;
    }
}
