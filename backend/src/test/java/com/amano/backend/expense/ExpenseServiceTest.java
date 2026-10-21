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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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
        existing.setDescription("Almuerzo");
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

    @Test
    void boundaryAmountsAreAcceptedWithoutRounding() {
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());

        for (String amount : new String[] { "0.01", "999999999.99", "1.10" }) {
            RegisterExpenseRequest request = validRequest();
            request.setAmount(amount);

            service.registerExpense(USER_ID, KEY, request);
        }

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository, Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(expense -> expense.getAmount().toPlainString())
                .containsExactly("0.01", "999999999.99", "1.10");
    }

    @Test
    void descriptionAtTheLimitIsAccepted() {
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());
        RegisterExpenseRequest request = validRequest();
        request.setDescription("a".repeat(ExpenseDescriptionNormalizer.MAX_CODE_POINTS));

        service.registerExpense(USER_ID, KEY, request);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).hasSize(200);
    }

    @Test
    void everyInvalidFieldIsAccumulatedInOnePassAndNothingIsSaved() {
        RegisterExpenseRequest request = validRequest();
        request.setAmount("1E+2");
        request.setCurrency("GBP");
        request.setDate(LocalDate.now(ZoneOffset.UTC).plusDays(2).toString());
        request.setCategory("PARTY");
        request.setType("WEEKLY");
        request.setPaymentMethod("BITCOIN");
        request.setDescription("a".repeat(ExpenseDescriptionNormalizer.MAX_CODE_POINTS + 1));

        FieldValidationException failure = failureOf(request);

        assertThat(failure).isNotNull();
        assertThat(failure.getFields()).containsOnlyKeys(
                "amount", "currency", "date", "category", "type", "paymentMethod",
                "description");
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void blankRequiredFieldsAreErrorsInsteadOfACrash() {
        RegisterExpenseRequest request = validRequest();
        request.setAmount("   ");
        request.setCurrency("");
        request.setDate(" ");
        request.setCategory("");
        request.setType("	");
        request.setPaymentMethod("  ");

        FieldValidationException failure = failureOf(request);

        assertThat(failure).isNotNull();
        assertThat(failure.getFields()).containsOnlyKeys(
                "amount", "currency", "date", "category", "type", "paymentMethod");
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void missingDeviceTimeZoneAppliesTheTolerance() {
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());
        RegisterExpenseRequest request = validRequest();
        request.setDeviceTimeZone(null);
        request.setDate(LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());

        service.registerExpense(USER_ID, KEY, request);

        verify(expenseRepository).save(any(Expense.class));
    }

    private static Expense storedExpense(RegisterExpenseRequest request) {
        Expense expense = new Expense();
        expense.setId(99L);
        expense.setUserId(USER_ID);
        expense.setAmount(ExpenseAmountNormalizer.normalize(request.getAmount()));
        expense.setCurrency(Currency.valueOf(request.getCurrency()));
        expense.setExpenseDate(LocalDate.parse(request.getDate()));
        expense.setCategory(ExpenseCategory.valueOf(request.getCategory()));
        expense.setType(ExpenseType.valueOf(request.getType()));
        expense.setPaymentMethod(PaymentMethod.valueOf(request.getPaymentMethod()));
        expense.setDescription(ExpenseDescriptionNormalizer.normalize(request.getDescription()));
        expense.setIdempotencyKey(KEY);
        return expense;
    }

    @Test
    void matchingPayloadReplaysTheOriginalWithoutInserting() {
        RegisterExpenseRequest request = validRequest();
        Expense existing = storedExpense(request);
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(existing));

        ExpenseService.Registration registration = service.registerExpense(USER_ID, KEY, request);

        assertThat(registration.created()).isFalse();
        assertThat(registration.expense().id()).isEqualTo(99L);
        assertThat(registration.expense().description()).isEqualTo("Almuerzo");
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void replayComparesAmountsByValueNotByScale() {
        RegisterExpenseRequest request = validRequest();
        Expense existing = storedExpense(request);
        existing.setAmount(new BigDecimal("10.1"));
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(existing));

        ExpenseService.Registration registration = service.registerExpense(USER_ID, KEY, request);

        assertThat(registration.created()).isFalse();
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void anyDifferentNormalizedFieldIsAnIdempotencyConflictWithoutInserting() {
        List<Consumer<RegisterExpenseRequest>> mutations = List.of(
                request -> request.setAmount("10.11"),
                request -> request.setCurrency("USD"),
                request -> request.setDate(LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()),
                request -> request.setCategory("OTHER"),
                request -> request.setType("RECURRING"),
                request -> request.setPaymentMethod("TRANSFER"),
                request -> request.setDescription("Cena"),
                request -> request.setDescription(" "));

        for (Consumer<RegisterExpenseRequest> mutation : mutations) {
            RegisterExpenseRequest original = validRequest();
            Expense existing = storedExpense(original);
            RegisterExpenseRequest changed = validRequest();
            mutation.accept(changed);
            when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                    .thenReturn(Optional.of(existing));

            IdempotencyConflictException conflict = catchThrowableOfType(
                    () -> service.registerExpense(USER_ID, KEY, changed),
                    IdempotencyConflictException.class);

            assertThat(conflict).isNotNull();
            assertThat(conflict).hasMessage(IdempotencyConflictException.MESSAGE);
        }
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void reservedPropertiesAreRejectedOneByOneWithoutInserting() {
        List<String> reserved = List.of(
                "userId", "creatorId", "creator", "user", "participants", "participantIds",
                "splits", "divisions", "shares", "debts", "friends", "groups");

        for (String property : reserved) {
            RegisterExpenseRequest request = validRequest();
            request.putUnknownProperty(property, "otro-usuario");

            FieldValidationException failure = failureOf(request);

            assertThat(failure).isNotNull();
            assertThat(failure.getFields()).containsOnlyKeys(property);
            assertThat(failure.getFields().get(property)).isEqualTo(
                    "No se permite informar este campo");
        }
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void allReservedPropertiesAreAccumulatedInOnePass() {
        RegisterExpenseRequest request = validRequest();
        request.putUnknownProperty("userId", 1);
        request.putUnknownProperty("participants", List.of(2));
        request.putUnknownProperty("splits", List.of(Map.of("userId", 2)));

        FieldValidationException failure = failureOf(request);

        assertThat(failure).isNotNull();
        assertThat(failure.getFields()).containsOnlyKeys("userId", "participants", "splits");
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void nonReservedUnknownPropertiesAreIgnored() {
        RegisterExpenseRequest request = validRequest();
        request.putUnknownProperty("theme", "dark");
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());

        service.registerExpense(USER_ID, KEY, request);

        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    void concurrentWinnerWithSamePayloadIsReplayed() {
        RegisterExpenseRequest request = validRequest();
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(storedExpense(request)));

        ExpenseService.Registration registration = service.resolveConcurrentRegistration(
                USER_ID, KEY, request);

        assertThat(registration.created()).isFalse();
        assertThat(registration.expense().id()).isEqualTo(99L);
    }

    @Test
    void concurrentWinnerWithDifferentPayloadIsAConflict() {
        RegisterExpenseRequest request = validRequest();
        RegisterExpenseRequest changed = validRequest();
        changed.setAmount("99.99");
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(storedExpense(request)));

        IdempotencyConflictException conflict = catchThrowableOfType(
                () -> service.resolveConcurrentRegistration(USER_ID, KEY, changed),
                IdempotencyConflictException.class);

        assertThat(conflict).isNotNull();
    }

    @Test
    void concurrentRaceWithoutAWinnerIsAnUnexpectedError() {
        RegisterExpenseRequest request = validRequest();
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty());

        IllegalStateException failure = catchThrowableOfType(
                () -> service.resolveConcurrentRegistration(USER_ID, KEY, request),
                IllegalStateException.class);

        assertThat(failure).isNotNull();
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    private void assertOnlyField(String field, RegisterExpenseRequest request) {
        FieldValidationException failure = failureOf(request);

        assertThat(failure).isNotNull();
        assertThat(failure.getFields()).containsOnlyKeys(field);
        verify(expenseRepository, never()).save(any(Expense.class));
    }
}
