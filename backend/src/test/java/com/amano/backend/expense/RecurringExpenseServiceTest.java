package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.Currency;

/**
 * Unit tests of {@link RecurringExpenseService} (009): the state machine of
 * FR-007..FR-011 and the daily generation of FR-005/FR-006, with a fixed clock
 * on 24/10/2026 (America/Argentina/Buenos_Aires).
 */
class RecurringExpenseServiceTest {

    private static final Long OWNER = 7L;
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-24");

    private ExpenseRepository expenseRepository;
    private RecurringExpenseService service;
    private final List<Expense> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        expenseRepository = Mockito.mock(ExpenseRepository.class);
        Clock clock = Clock.fixed(TODAY.atTime(15, 0).atZone(ZONE).toInstant(), ZONE);
        service = new RecurringExpenseService(expenseRepository, clock);
        saved.clear();
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            saved.add(expense);
            return expense;
        });
    }

    private static Expense series(long id, RecurrenceFrequency frequency, String start,
            RecurrenceStatus status, String next) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setUserId(OWNER);
        expense.setAmount(new BigDecimal("250000.00"));
        expense.setCurrency(Currency.ARS);
        expense.setExpenseDate(LocalDate.parse(start));
        expense.setCategory(ExpenseCategory.HOUSING);
        expense.setType(ExpenseType.RECURRING);
        expense.setPaymentMethod(PaymentMethod.TRANSFER);
        expense.setDescription("Alquiler");
        expense.setIdempotencyKey(UUID.randomUUID());
        expense.setRecurrenceFrequency(frequency);
        expense.setRecurrenceStatus(status);
        expense.setRecurrenceNextDate(next == null ? null : LocalDate.parse(next));
        return expense;
    }

    private void owned(Expense expense) {
        when(expenseRepository.findByIdAndUserId(expense.getId(), OWNER)).thenReturn(Optional.of(expense));
    }

    // ----- State changes -------------------------------------------------------

    @Test
    void pausingAnActiveSeriesKeepsItsNextDate() {
        Expense series = series(1, RecurrenceFrequency.MONTHLY, "2026-10-01", RecurrenceStatus.ACTIVE, "2026-11-01");
        owned(series);

        ExpenseResponse response = service.changeStatus(OWNER, 1L, "PAUSED");

        assertThat(series.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.PAUSED);
        assertThat(series.getRecurrenceNextDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(response.status()).isEqualTo("PAUSED");
        assertThat(response.nextOccurrenceDate()).isEqualTo("2026-11-01");
    }

    @Test
    void resumingSkipsTheDatesOfThePause() {
        // US3 scenario 3: start 05/08, paused with next 05/09, resumed on 24/10 → 05/11.
        Expense series = series(1, RecurrenceFrequency.MONTHLY, "2026-08-05", RecurrenceStatus.PAUSED, "2026-09-05");
        owned(series);

        ExpenseResponse response = service.changeStatus(OWNER, 1L, "ACTIVE");

        assertThat(series.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.ACTIVE);
        assertThat(series.getRecurrenceNextDate()).isEqualTo(LocalDate.parse("2026-11-05"));
        assertThat(response.nextOccurrenceDate()).isEqualTo("2026-11-05");
    }

    @Test
    void resumingOnASeriesDateKeepsToday() {
        // US3 scenario 4: start 24/09, resumed on 24/10 → today.
        Expense series = series(1, RecurrenceFrequency.MONTHLY, "2026-09-24", RecurrenceStatus.PAUSED, "2026-10-24");
        owned(series);

        service.changeStatus(OWNER, 1L, "ACTIVE");

        assertThat(series.getRecurrenceNextDate()).isEqualTo(TODAY);
    }

    @Test
    void finishingClearsTheNextDateFromActiveOrPaused() {
        Expense active = series(1, RecurrenceFrequency.WEEKLY, "2026-10-01", RecurrenceStatus.ACTIVE, "2026-10-29");
        Expense paused = series(2, RecurrenceFrequency.YEARLY, "2026-01-10", RecurrenceStatus.PAUSED, "2027-01-10");
        owned(active);
        owned(paused);

        ExpenseResponse response = service.changeStatus(OWNER, 1L, "FINISHED");
        service.changeStatus(OWNER, 2L, "FINISHED");

        assertThat(active.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.FINISHED);
        assertThat(active.getRecurrenceNextDate()).isNull();
        assertThat(paused.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.FINISHED);
        assertThat(paused.getRecurrenceNextDate()).isNull();
        assertThat(response.status()).isEqualTo("FINISHED");
        assertThat(response.nextOccurrenceDate()).isNull();
    }

    @Test
    void leavingAFinishedSeriesIsAConflict() {
        Expense finished = series(1, RecurrenceFrequency.MONTHLY, "2026-01-01", RecurrenceStatus.FINISHED, null);
        owned(finished);

        for (String target : List.of("ACTIVE", "PAUSED")) {
            assertThatThrownBy(() -> service.changeStatus(OWNER, 1L, target))
                    .isInstanceOf(RecurringExpenseFinishedException.class)
                    .hasMessage("El gasto recurrente ya está finalizado y no puede modificarse");
        }
        assertThat(finished.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.FINISHED);
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void askingForTheCurrentStatusIsANoOpSuccess() {
        Expense active = series(1, RecurrenceFrequency.MONTHLY, "2026-10-01", RecurrenceStatus.ACTIVE, "2026-11-01");
        Expense paused = series(2, RecurrenceFrequency.MONTHLY, "2026-08-05", RecurrenceStatus.PAUSED, "2026-09-05");
        Expense finished = series(3, RecurrenceFrequency.MONTHLY, "2026-01-01", RecurrenceStatus.FINISHED, null);
        owned(active);
        owned(paused);
        owned(finished);

        assertThat(service.changeStatus(OWNER, 1L, "ACTIVE").nextOccurrenceDate()).isEqualTo("2026-11-01");
        assertThat(service.changeStatus(OWNER, 2L, "PAUSED").nextOccurrenceDate()).isEqualTo("2026-09-05");
        assertThat(service.changeStatus(OWNER, 3L, "FINISHED").status()).isEqualTo("FINISHED");
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void anythingThatIsNotASeriesOfTheOwnerIsNotFound() {
        Expense oneTime = series(1, null, "2026-10-01", null, null);
        oneTime.setType(ExpenseType.ONE_TIME);
        Expense occurrence = series(2, null, "2026-10-01", null, null);
        occurrence.setRecurrenceSourceId(9L);
        Expense legacy = series(3, null, "2026-10-01", null, null);
        owned(oneTime);
        owned(occurrence);
        owned(legacy);
        when(expenseRepository.findByIdAndUserId(4L, OWNER)).thenReturn(Optional.empty()); // foreign or missing

        for (long id = 1; id <= 4; id++) {
            long target = id;
            assertThatThrownBy(() -> service.changeStatus(OWNER, target, "PAUSED"))
                    .isInstanceOf(RecurringExpenseNotFoundException.class)
                    .hasMessage("No se encontró el gasto recurrente");
        }
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void missingOrUnknownStatusIsAFieldError() {
        assertThatThrownBy(() -> service.changeStatus(OWNER, 1L, null))
                .isInstanceOfSatisfying(FieldValidationException.class, failure ->
                        assertThat(failure.getFields()).containsEntry("status", "El estado es obligatorio"));
        assertThatThrownBy(() -> service.changeStatus(OWNER, 1L, "STOPPED"))
                .isInstanceOfSatisfying(FieldValidationException.class, failure ->
                        assertThat(failure.getFields()).containsEntry("status", "El estado no es válido"));
        verify(expenseRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void listReturnsTheOwnerSeriesInRepositoryOrder() {
        Expense newer = series(2, RecurrenceFrequency.WEEKLY, "2026-10-20", RecurrenceStatus.ACTIVE, "2026-10-27");
        Expense older = series(1, RecurrenceFrequency.MONTHLY, "2026-09-01", RecurrenceStatus.FINISHED, null);
        when(expenseRepository.findByUserIdAndRecurrenceFrequencyIsNotNullOrderByCreatedAtDescIdDesc(OWNER))
                .thenReturn(List.of(newer, older));

        List<ExpenseResponse> list = service.listSeries(OWNER);

        assertThat(list).extracting(ExpenseResponse::id).containsExactly(2L, 1L);
        assertThat(list).extracting(ExpenseResponse::status).containsExactly("ACTIVE", "FINISHED");
    }

    @Test
    void todayComesFromTheRecurrenceClock() {
        assertThat(service.today()).isEqualTo(TODAY);
    }

    // ----- Generation ---------------------------------------------------------

    private void due(Expense... series) {
        dueOn(TODAY, series);
    }

    private void dueOn(LocalDate date, Expense... series) {
        when(expenseRepository.findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc(
                RecurrenceStatus.ACTIVE, date)).thenReturn(List.of(series));
    }

    private List<Expense> occurrences() {
        return saved.stream().filter(expense -> expense.getRecurrenceSourceId() != null).toList();
    }

    @Test
    void dueSeriesGeneratesACopyAndAdvancesTheNextDate() {
        Expense series = series(5, RecurrenceFrequency.MONTHLY, "2026-09-24", RecurrenceStatus.ACTIVE, "2026-10-24");
        due(series);

        int created = service.generateDueOccurrences(TODAY);

        assertThat(created).isEqualTo(1);
        Expense occurrence = occurrences().get(0);
        assertThat(occurrence.getUserId()).isEqualTo(OWNER);
        assertThat(occurrence.getAmount()).isEqualByComparingTo("250000.00");
        assertThat(occurrence.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(occurrence.getCategory()).isEqualTo(ExpenseCategory.HOUSING);
        assertThat(occurrence.getPaymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(occurrence.getDescription()).isEqualTo("Alquiler");
        assertThat(occurrence.getType()).isEqualTo(ExpenseType.RECURRING);
        assertThat(occurrence.getExpenseDate()).isEqualTo(TODAY);
        assertThat(occurrence.getRecurrenceSourceId()).isEqualTo(5L);
        assertThat(occurrence.getRecurrenceFrequency()).isNull();
        assertThat(occurrence.getRecurrenceStatus()).isNull();
        assertThat(occurrence.getRecurrenceNextDate()).isNull();
        assertThat(occurrence.getIdempotencyKey())
                .isEqualTo(RecurringExpenseService.occurrenceKey(5L, TODAY));
        assertThat(series.getRecurrenceNextDate()).isEqualTo(LocalDate.parse("2026-11-24"));
    }

    @Test
    void missedRunsCatchUpEveryDueDateOfAnActiveSeries() {
        // US2 scenario 3: weekly series with next 03/10, job down until 24/10.
        Expense series = series(5, RecurrenceFrequency.WEEKLY, "2026-09-26", RecurrenceStatus.ACTIVE, "2026-10-03");
        due(series);

        int created = service.generateDueOccurrences(TODAY);

        assertThat(created).isEqualTo(4);
        assertThat(occurrences()).extracting(Expense::getExpenseDate).containsExactly(
                LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-10"),
                LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-24"));
        assertThat(series.getRecurrenceNextDate()).isEqualTo(LocalDate.parse("2026-10-31"));
    }

    @Test
    void monthEndSeriesGeneratesClampedDates() {
        Expense series = series(5, RecurrenceFrequency.MONTHLY, "2026-01-31", RecurrenceStatus.ACTIVE, "2026-02-28");
        dueOn(LocalDate.parse("2026-04-30"), series);

        service.generateDueOccurrences(LocalDate.parse("2026-04-30"));

        assertThat(occurrences()).extracting(Expense::getExpenseDate).containsExactly(
                LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-30"));
        assertThat(series.getRecurrenceNextDate()).isEqualTo(LocalDate.parse("2026-05-31"));
    }

    @Test
    void occurrenceKeysAreDeterministicPerSeriesAndDate() {
        assertThat(RecurringExpenseService.occurrenceKey(5L, TODAY))
                .isEqualTo(RecurringExpenseService.occurrenceKey(5L, TODAY))
                .isNotEqualTo(RecurringExpenseService.occurrenceKey(6L, TODAY))
                .isNotEqualTo(RecurringExpenseService.occurrenceKey(5L, TODAY.plusDays(1)));
    }

    @Test
    void nothingDueGeneratesNothing() {
        due();

        assertThat(service.generateDueOccurrences(TODAY)).isZero();
        verify(expenseRepository, never()).save(any(Expense.class));
    }
}
