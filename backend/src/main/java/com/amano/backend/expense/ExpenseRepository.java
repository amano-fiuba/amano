package com.amano.backend.expense;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.amano.backend.common.CurrencyDateTotal;

/**
 * Persistence for {@code expenses}. The unique index
 * {@code uq_expenses_user_idempotency_key} (FR-027) is the race-proof backstop;
 * the service resolves conflicts by re-reading the winner and comparing payloads
 * (research.md D1).
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long>,
        JpaSpecificationExecutor<Expense> {

    Optional<Expense> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    /** Latest expenses registered by a user (015 FR-008). */
    List<Expense> findTop5ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** Years (by expense date) with at least one expense of the user, newest first (031, FR-006). */
    @Query("select distinct year(e.expenseDate) as y from Expense e "
            + "where e.userId = :userId order by y desc")
    List<Integer> findExpenseYears(@Param("userId") Long userId);

    /**
     * Expenses already registered in {@code [from, to]}, summed per currency and
     * expense date: what the personal balance converts (specs/033-income-management,
     * FR-010). One row per {@code (currency, date)}.
     */
    @Query("select new com.amano.backend.common.CurrencyDateTotal("
            + "e.currency, e.expenseDate, sum(e.amount)) from Expense e "
            + "where e.userId = :userId and e.expenseDate >= :from and e.expenseDate <= :to "
            + "group by e.currency, e.expenseDate")
    List<CurrencyDateTotal> sumByCurrencyAndDate(@Param("userId") Long userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Personal expenses of a user between two expense dates (inclusive), summed by
     * category, currency and date for the statistics (032, FR-006, plan D2).
     */
    @Query("select new com.amano.backend.expense.CategorySubtotal("
            + "e.category, e.currency, e.expenseDate, sum(e.amount), count(e)) "
            + "from Expense e where e.userId = :userId and e.expenseDate between :from and :to "
            + "group by e.category, e.currency, e.expenseDate")
    List<CategorySubtotal> sumByCategoryCurrencyAndDate(@Param("userId") Long userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Series of a user for the "Gastos recurrentes" screen, newest first (009, FR-012). */
    List<Expense> findByUserIdAndRecurrenceFrequencyIsNotNullOrderByCreatedAtDescIdDesc(Long userId);

    /** Active series whose next date is due (009, FR-005). */
    List<Expense> findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc(
            RecurrenceStatus status, LocalDate date);
}
