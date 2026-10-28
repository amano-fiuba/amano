package com.amano.backend.expense;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@code expenses}. The unique index
 * {@code uq_expenses_user_idempotency_key} (FR-027) is the race-proof backstop;
 * the service resolves conflicts by re-reading the winner and comparing payloads
 * (research.md D1).
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    /** Series of a user for the "Gastos recurrentes" screen, newest first (009, FR-012). */
    List<Expense> findByUserIdAndRecurrenceFrequencyIsNotNullOrderByCreatedAtDescIdDesc(Long userId);

    /** Active series whose next date is due (009, FR-005). */
    List<Expense> findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc(
            RecurrenceStatus status, LocalDate date);
}
