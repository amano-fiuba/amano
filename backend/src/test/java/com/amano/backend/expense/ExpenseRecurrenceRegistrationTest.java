package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;

/**
 * Unit tests of the 009 rules added to {@link ExpenseService#registerExpense}
 * (FR-001..FR-003): RECURRING requires a catalog frequency, ONE_TIME rejects it,
 * and a new series starts ACTIVE with a non-retroactive next date. The 007 rules
 * stay covered by {@code ExpenseServiceTest}.
 */
class ExpenseRecurrenceRegistrationTest {

    private static final Long USER_ID = 7L;
    private static final UUID KEY = UUID.fromString("9a1d2c3e-4f50-4a6b-8c7d-0e1f2a3b4c5d");

    private ExpenseRepository expenseRepository;
    private ExpenseService service;

    @BeforeEach
    void setUp() {
        expenseRepository = Mockito.mock(ExpenseRepository.class);
        service = new ExpenseService(expenseRepository);
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
    }

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static RegisterExpenseRequest request(String type, String frequency, LocalDate date) {
        RegisterExpenseRequest request = new RegisterExpenseRequest();
        request.setAmount("250000.00");
        request.setCurrency("ARS");
        request.setDate(date.toString());
        request.setCategory("HOUSING");
        request.setType(type);
        request.setFrequency(frequency);
        request.setPaymentMethod("TRANSFER");
        request.setDescription("Alquiler");
        request.setDeviceTimeZone("UTC");
        return request;
    }

    private Expense saved() {
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        return captor.getValue();
    }

    private FieldValidationException failureOf(RegisterExpenseRequest request) {
        return catchThrowableOfType(() -> service.registerExpense(USER_ID, KEY, request),
                FieldValidationException.class);
    }

    @Test
    void recurringExpenseStartsAnActiveSeriesWithTheNextDate() {
        LocalDate today = todayUtc();

        ExpenseService.Registration registration =
                service.registerExpense(USER_ID, KEY, request("RECURRING", "MONTHLY", today));

        Expense series = saved();
        assertThat(series.getType()).isEqualTo(ExpenseType.RECURRING);
        assertThat(series.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
        assertThat(series.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.ACTIVE);
        assertThat(series.getRecurrenceNextDate()).isEqualTo(today.plusMonths(1));
        assertThat(series.getRecurrenceSourceId()).isNull();
        assertThat(registration.expense().frequency()).isEqualTo("MONTHLY");
        assertThat(registration.expense().status()).isEqualTo("ACTIVE");
        assertThat(registration.expense().nextOccurrenceDate())
                .isEqualTo(today.plusMonths(1).toString());
    }

    @Test
    void pastStartDateGetsTheFirstSeriesDateAfterTodayNotARetroactiveOne() {
        LocalDate today = todayUtc();
        LocalDate start = today.minusWeeks(5);

        service.registerExpense(USER_ID, KEY, request("RECURRING", "WEEKLY", start));

        LocalDate next = saved().getRecurrenceNextDate();
        assertThat(next).isAfter(today);
        assertThat(next).isBeforeOrEqualTo(today.plusWeeks(1));
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(start, next) % 7).isZero();
    }

    @Test
    void recurringWithoutFrequencyIsAFieldError() {
        FieldValidationException failure = failureOf(request("RECURRING", null, todayUtc()));

        assertThat(failure.getFields())
                .containsExactly(java.util.Map.entry("frequency",
                        "La frecuencia es obligatoria para un gasto recurrente"));
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void blankFrequencyCountsAsMissing() {
        assertThat(failureOf(request("RECURRING", "  ", todayUtc())).getFields())
                .containsEntry("frequency", "La frecuencia es obligatoria para un gasto recurrente");
    }

    @Test
    void frequencyOutsideTheCatalogIsAFieldError() {
        assertThat(failureOf(request("RECURRING", "DAILY", todayUtc())).getFields())
                .containsEntry("frequency", "La frecuencia no es válida");
        assertThat(failureOf(request("RECURRING", "monthly", todayUtc())).getFields())
                .containsEntry("frequency", "La frecuencia no es válida");
    }

    @Test
    void oneTimeWithFrequencyIsRejected() {
        assertThat(failureOf(request("ONE_TIME", "MONTHLY", todayUtc())).getFields())
                .containsExactly(java.util.Map.entry("frequency", "Un gasto único no puede tener frecuencia"));
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void oneTimeExpenseKeepsTheRecurrenceColumnsEmpty() {
        ExpenseService.Registration registration =
                service.registerExpense(USER_ID, KEY, request("ONE_TIME", null, todayUtc()));

        Expense expense = saved();
        assertThat(expense.getRecurrenceFrequency()).isNull();
        assertThat(expense.getRecurrenceStatus()).isNull();
        assertThat(expense.getRecurrenceNextDate()).isNull();
        assertThat(registration.expense().frequency()).isNull();
        assertThat(registration.expense().status()).isNull();
        assertThat(registration.expense().nextOccurrenceDate()).isNull();
    }

    @Test
    void frequencyErrorsAreAccumulatedWithThe007Errors() {
        RegisterExpenseRequest request = request("RECURRING", null, todayUtc());
        request.setAmount("");

        assertThat(failureOf(request).getFields()).containsKeys("amount", "frequency");
    }

    @Test
    void invalidTypeOnlyChecksTheFrequencyCatalog() {
        assertThat(failureOf(request("OTHER", null, todayUtc())).getFields())
                .containsOnlyKeys("type");
        assertThat(failureOf(request("OTHER", "DAILY", todayUtc())).getFields())
                .containsOnlyKeys("type", "frequency");
    }

    @Test
    void replayWithAnotherFrequencyIsAnIdempotencyConflict() {
        RegisterExpenseRequest original = request("RECURRING", "MONTHLY", todayUtc());
        Expense existing = new Expense();
        existing.setId(99L);
        existing.setUserId(USER_ID);
        existing.setAmount(ExpenseAmountNormalizer.normalize(original.getAmount()));
        existing.setCurrency(com.amano.backend.user.Currency.ARS);
        existing.setExpenseDate(todayUtc());
        existing.setCategory(ExpenseCategory.HOUSING);
        existing.setType(ExpenseType.RECURRING);
        existing.setPaymentMethod(PaymentMethod.TRANSFER);
        existing.setDescription("Alquiler");
        existing.setIdempotencyKey(KEY);
        existing.setRecurrenceFrequency(RecurrenceFrequency.MONTHLY);
        existing.setRecurrenceStatus(RecurrenceStatus.ACTIVE);
        existing.setRecurrenceNextDate(todayUtc().plusMonths(1));
        when(expenseRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(existing));

        assertThat(service.registerExpense(USER_ID, KEY, original).created()).isFalse();
        IdempotencyConflictException conflict = catchThrowableOfType(
                () -> service.registerExpense(USER_ID, KEY, request("RECURRING", "WEEKLY", todayUtc())),
                IdempotencyConflictException.class);
        assertThat(conflict).isNotNull();
        verify(expenseRepository, never()).save(any(Expense.class));
    }
}
