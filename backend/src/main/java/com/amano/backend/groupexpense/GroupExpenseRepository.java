package com.amano.backend.groupexpense;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.amano.backend.expense.RecurrenceStatus;

/** Persistence of {@link GroupExpense} (017). */
public interface GroupExpenseRepository extends JpaRepository<GroupExpense, Long> {

    Optional<GroupExpense> findByCreatedByUserIdAndIdempotencyKey(Long createdByUserId, UUID idempotencyKey);

    Optional<GroupExpense> findByIdAndGroupId(Long id, Long groupId);

    /** Active series whose next occurrence is due, oldest first. */
    List<GroupExpense> findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc(
            RecurrenceStatus status, LocalDate date);

    /** Backstop of the deterministic occurrence key (V32 unique constraint). */
    Optional<GroupExpense> findByRecurrenceSourceIdAndExpenseDate(Long recurrenceSourceId, LocalDate expenseDate);

    /** 027 US4: an expense with a friend whose two participants are {@code a} and {@code b}. */
    @Query("""
            SELECT e FROM GroupExpense e
            WHERE e.id = :id AND e.groupId IS NULL
              AND ((e.createdByUserId = :a AND e.friendUserId = :b)
                OR (e.createdByUserId = :b AND e.friendUserId = :a))
            """)
    Optional<GroupExpense> findDirectBetween(@Param("id") Long id, @Param("a") Long a, @Param("b") Long b);
}
