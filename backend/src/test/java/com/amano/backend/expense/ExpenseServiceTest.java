package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.Currency;

/**
 * Unit tests of {@link ExpenseService} (T021): normalization/validation happens in
 * one pass, required fields never fall through to a 500, catalog values come only
 * from the enums, and an existing operation is replayed without inserting.
 */
class ExpenseServiceTest {

    private static final Long USER_ID = 7L;
    private static final UUID KEY = UUID.fromString("3f1c8a8e-6a4e-4f0e-9b1e-0d8f5f3c2a10");

    private ExpenseRepository expenseRepository;
    private ExpenseService service;

    @BeforeEach
    void setUp() {
        expenseRepository = Mockito.mock(ExpenseRepository.class);
        service = new ExpenseService(expenseRepository);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
    }

    private static RegisterExpenseRequest validRequest() {
        RegisterExpenseRequest request = new RegisterExpenseRequest();
        request.setAmount("10.10");
        request.setCurrency("ARS");
        request.setDate(LocalDate.now(ZoneOffset.UTC).toString());
        request.setCategory("FOOD");
        request.setType("ONE_TIME");
        request.setPaymentMethod("CASH");
        request.setDescription("  Almuerzo  ");
        request.setDeviceTimeZone("UTC");
        return request;
    }

    private FieldValidationException failureOf(RegisterExpenseRequest request) {
        return catchThrowableOfType(
                () -> service.registerExpense(USER_ID, KEY, request),
                FieldValidationException.class);
    }

    @Test
    void validRequestPersistsLinkedToTheSessionUserWithNormalizedValues() {
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());

        ExpenseService.Registration registration = service.registerExpense(
                USER_ID, KEY, validRequest());

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("10.10"));
        assertThat(saved.getAmount().scale()).isEqualTo(2);
        assertThat(saved.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(saved.getExpenseDate()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(saved.getCategory()).isEqualTo(ExpenseCategory.FOOD);
        assertThat(saved.getType()).isEqualTo(ExpenseType.ONE_TIME);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getDescription()).isEqualTo("Almuerzo");
        assertThat(saved.getIdempotencyKey()).isEqualTo(KEY);

        assertThat(registration.created()).isTrue();
        assertThat(registration.expense().id()).isEqualTo(42L);
        assertThat(registration.expense().amount()).isEqualTo("10.10");
        assertThat(registration.expense().currency()).isEqualTo("ARS");
        assertThat(registration.expense().description()).isEqualTo("Almuerzo");
    }

    @Test
    void oneDecimalAmountIsNormalizedToTheContractScale() {
        RegisterExpenseRequest request = validRequest();
        request.setAmount("10.1");
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());

        service.registerExpense(USER_ID, KEY, request);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount().toPlainString()).isEqualTo("10.10");
    }

    @Test
    void blankDescriptionIsPersistedAsNull() {
        RegisterExpenseRequest request = validRequest();
        request.setDescription(" \t\n ");
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());

        service.registerExpense(USER_ID, KEY, request);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    void missingAmountIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setAmount(null);
        assertOnlyField("amount", request);
    }

    @Test
    void missingCurrencyIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setCurrency(null);
        assertOnlyField("currency", request);
    }

    @Test
    void missingDateIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setDate(null);
        assertOnlyField("date", request);
    }

    @Test
    void missingCategoryIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setCategory(null);
        assertOnlyField("category", request);
    }

    @Test
    void missingTypeIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setType(null);
        assertOnlyField("type", request);
    }

    @Test
    void missingPaymentMethodIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setPaymentMethod(null);
        assertOnlyField("paymentMethod", request);
    }

    @Test
    void allRequiredFieldsMissingAreAccumulatedInOnePass() {
        RegisterExpenseRequest request = validRequest();
        request.setAmount(null);
        request.setCurrency(null);
        request.setDate(null);
        request.setCategory(null);
        request.setType(null);
        request.setPaymentMethod(null);

        FieldValidationException failure = failureOf(request);

        assertThat(failure).isNotNull();
        assertThat(failure.getFields()).containsOnlyKeys(
                "amount", "currency", "date", "category", "type", "paymentMethod");
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void manipulatedCatalogValuesAreAccumulatedInOnePass() {
        RegisterExpenseRequest request = validRequest();
        request.setCurrency("GBP");
        request.setCategory("PARTY");
        request.setType("WEEKLY");
        request.setPaymentMethod("BITCOIN");

        FieldValidationException failure = failureOf(request);

        assertThat(failure).isNotNull();
        assertThat(failure.getFields()).containsOnlyKeys(
                "currency", "category", "type", "paymentMethod");
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void invalidAmountFormatsAreFieldErrorsWithoutSaving() {
        for (String amount : new String[] { "abc", "10.101", "0", "-1", "1000000000.00", "10,10" }) {
            RegisterExpenseRequest request = validRequest();
            request.setAmount(amount);

            FieldValidationException failure = failureOf(request);

            assertThat(failure).as("amount %s", amount).isNotNull();
            assertThat(failure.getFields()).containsOnlyKeys("amount");
        }
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void unrecognizedDateFormatIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setDate("21/10/2026");
        assertOnlyField("date", request);
    }

    @Test
    void futureDateIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setDeviceTimeZone("UTC");
        request.setDate(LocalDate.now(ZoneOffset.UTC).plusDays(2).toString());
        assertOnlyField("date", request);
    }

    @Test
    void tooLongDescriptionIsAFieldErrorWithoutSaving() {
        RegisterExpenseRequest request = validRequest();
        request.setDescription("a".repeat(ExpenseDescriptionNormalizer.MAX_CODE_POINTS + 1));
        assertOnlyField("description", request);
    }

    @Test
    void existingOperationIsReplayedWithoutInserting() {
        Expense existing = new Expense();
        existing.setId(99L);
        existing.setUserId(USER_ID);
        existing.setAmount(new BigDecimal("10.10"));
        existing.setCurrency(Currency.ARS);
        existing.setExpenseDate(LocalDate.now(ZoneOffset.UTC));
        existing.setCategory(ExpenseCategory.FOOD);
        existing.setType(ExpenseType.ONE_TIME);
        existing.setPaymentMethod(PaymentMethod.CASH);
        existing.setIdempotencyKey(KEY);
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(existing));

        ExpenseService.Registration registration = service.registerExpense(
                USER_ID, KEY, validRequest());

        verify(expenseRepository, never()).save(any(Expense.class));
        assertThat(registration.created()).isFalse();
        assertThat(registration.expense().id()).isEqualTo(99L);
        assertThat(registration.expense().amount()).isEqualTo("10.10");
    }

    private void assertOnlyField(String field, RegisterExpenseRequest request) {
        FieldValidationException failure = failureOf(request);

        assertThat(failure).isNotNull();
        assertThat(failure.getFields()).containsOnlyKeys(field);
        verify(expenseRepository, never()).save(any(Expense.class));
    }
}
