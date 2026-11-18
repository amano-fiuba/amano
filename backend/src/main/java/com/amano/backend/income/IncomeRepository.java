package com.amano.backend.income;

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
 * Persistence for {@code incomes}. The unique index
 * {@code uq_incomes_user_idempotency_key} is the race-proof backstop of the
 * idempotent registration, as {@code uq_expenses_user_idempotency_key} is for
 * expenses (specs/033-income-management/research.md R2).
 */
public interface IncomeRepository extends JpaRepository<Income, Long>,
        JpaSpecificationExecutor<Income> {

    Optional<Income> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);

    /** An income of the user; empty for a missing or foreign id (one neutral 404). */
    Optional<Income> findByIdAndUserId(Long id, Long userId);

    /** Years (by income date) with at least one income of the user, newest first. */
    @Query("select distinct year(i.incomeDate) as y from Income i "
            + "where i.userId = :userId order by y desc")
    List<Integer> findIncomeYears(@Param("userId") Long userId);

    /**
     * Incomes of the user in {@code [from, to]}, summed per currency and income date:
     * what the personal balance converts (FR-010). One row per {@code (currency, date)}.
     */
    @Query("select new com.amano.backend.common.CurrencyDateTotal("
            + "i.currency, i.incomeDate, sum(i.amount)) from Income i "
            + "where i.userId = :userId and i.incomeDate >= :from and i.incomeDate <= :to "
            + "group by i.currency, i.incomeDate")
    List<CurrencyDateTotal> sumByCurrencyAndDate(@Param("userId") Long userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
