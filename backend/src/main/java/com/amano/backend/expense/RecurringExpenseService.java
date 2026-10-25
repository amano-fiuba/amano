package com.amano.backend.expense;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;

/**
 * Recurring expense series (specs/009-pause-recurring-expense): listing, state
 * changes and the daily generation of occurrences.
 *
 * <p>State machine (FR-007..FR-010): ACTIVE ⇄ PAUSED and ACTIVE/PAUSED → FINISHED.
 * Asking for the current state is a no-op success; leaving FINISHED is a 409.
 * Pausing keeps the next date; resuming moves it to the first series date on or
 * after today, so the dates of the pause are never generated; finishing clears it.
 *
 * <p>Generation (FR-005/FR-006): every ACTIVE series with a due next date gets one
 * occurrence per due series date, with the data of the series and a deterministic
 * idempotency key, so a second run the same day cannot duplicate anything (the
 * {@code uq_expenses_recurrence_occurrence} index is the backstop).
 */
@Service
public class RecurringExpenseService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);

    static final String STATUS_REQUIRED = "El estado es obligatorio";
    static final String STATUS_INVALID = "El estado no es válido";

    /** Safety bound per series and run: ~19 years of weekly dates after a long outage. */
    private static final int MAX_OCCURRENCES_PER_RUN = 1000;

    private final ExpenseRepository expenseRepository;
    private final Clock clock;

    @Autowired
    public RecurringExpenseService(ExpenseRepository expenseRepository,
            RecurrenceProperties properties) {
        this(expenseRepository, Clock.system(properties.zone()));
    }

    RecurringExpenseService(ExpenseRepository expenseRepository, Clock clock) {
        this.expenseRepository = expenseRepository;
        this.clock = clock;
    }

    /** Today in the recurrence zone (FR-005/FR-008). */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The user's series, newest first (FR-012). */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> listSeries(Long userId) {
        return expenseRepository
                .findByUserIdAndRecurrenceFrequencyIsNotNullOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    /**
     * Applies a state change requested by the owner (FR-008..FR-011).
     *
     * @throws FieldValidationException          status absent or outside the catalog (400)
     * @throws RecurringExpenseNotFoundException not a series of this user (404)
     * @throws RecurringExpenseFinishedException leaving FINISHED (409)
     */
    @Transactional
    public ExpenseResponse changeStatus(Long userId, Long expenseId, String rawStatus) {
        RecurrenceStatus target = parseStatus(rawStatus);
        Expense series = expenseRepository.findByIdAndUserId(expenseId, userId)
                .filter(Expense::isRecurringSeries)
                .orElseThrow(RecurringExpenseNotFoundException::new);

        RecurrenceStatus current = series.getRecurrenceStatus();
        if (current == target) {
            return ExpenseResponse.from(series);
        }
        if (current == RecurrenceStatus.FINISHED) {
            throw new RecurringExpenseFinishedException();
        }

        switch (target) {
            case PAUSED -> series.setRecurrenceStatus(RecurrenceStatus.PAUSED);
            case ACTIVE -> {
                series.setRecurrenceStatus(RecurrenceStatus.ACTIVE);
                series.setRecurrenceNextDate(series.getRecurrenceFrequency()
                        .firstOnOrAfter(series.getExpenseDate(), today()));
            }
            case FINISHED -> {
                series.setRecurrenceStatus(RecurrenceStatus.FINISHED);
                series.setRecurrenceNextDate(null);
            }
        }
        return ExpenseResponse.from(expenseRepository.save(series));
    }

    /**
     * Generates every due occurrence of the ACTIVE series up to {@code today}
     * (inclusive) and advances their next date. Returns how many were created.
     */
    @Transactional
    public int generateDueOccurrences(LocalDate today) {
        int created = 0;
        List<Expense> due = expenseRepository
                .findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc(
                        RecurrenceStatus.ACTIVE, today);
        for (Expense series : due) {
            int generated = 0;
            LocalDate date = series.getRecurrenceNextDate();
            while (!date.isAfter(today) && generated < MAX_OCCURRENCES_PER_RUN) {
                expenseRepository.save(occurrenceOf(series, date));
                generated++;
                date = series.getRecurrenceFrequency().firstAfter(series.getExpenseDate(), date);
            }
            series.setRecurrenceNextDate(date);
            expenseRepository.save(series);
            created += generated;
        }
        if (created > 0) {
            log.info("Recurring expenses: {} occurrence(s) generated for {} series", created, due.size());
        }
        return created;
    }

    private static Expense occurrenceOf(Expense series, LocalDate date) {
        Expense occurrence = new Expense();
        occurrence.setUserId(series.getUserId());
        occurrence.setAmount(series.getAmount());
        occurrence.setCurrency(series.getCurrency());
        occurrence.setExpenseDate(date);
        occurrence.setCategory(series.getCategory());
        occurrence.setType(ExpenseType.RECURRING);
        occurrence.setPaymentMethod(series.getPaymentMethod());
        occurrence.setDescription(series.getDescription());
        occurrence.setIdempotencyKey(occurrenceKey(series.getId(), date));
        occurrence.setRecurrenceSourceId(series.getId());
        return occurrence;
    }

    /** Deterministic key of the occurrence of a series on a date (FR-006). */
    static UUID occurrenceKey(Long seriesId, LocalDate date) {
        return UUID.nameUUIDFromBytes(
                ("amano-recurrence:" + seriesId + ":" + date).getBytes(StandardCharsets.UTF_8));
    }

    private static RecurrenceStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new FieldValidationException(Map.of("status", STATUS_REQUIRED));
        }
        try {
            return RecurrenceStatus.valueOf(raw);
        }
        catch (IllegalArgumentException exception) {
            throw new FieldValidationException(Map.of("status", STATUS_INVALID));
        }
    }
}
