package com.amano.backend.groupexpense;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Persistence of {@link Debt} (017). The two {@code existsOpen…} queries back
 * the real {@code DebtPort} (research.md D7): "open" means not PAID (PENDING
 * or PARTIALLY_PAID), served by the partial indexes {@code ix_debts_open_*} of
 * V13.
 */
public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByExpenseId(Long expenseId);

    /** All debts of a group, for the derived balances (022 D4). */
    List<Debt> findByGroupId(Long groupId);

    /**
     * 018 (data-model.md §Concurrencia, plan.md D5): the debt with a pessimistic
     * write lock ({@code SELECT … FOR UPDATE}), so concurrent payments of the
     * same debt are serialized and never pay more than its amount.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Debt d WHERE d.id = :id")
    Optional<Debt> findByIdForUpdate(@Param("id") Long id);

    /**
     * 022 T043: open debts of the group in which {@code userId} is the debtor
     * or the creditor. The four states are exactly the ones that contribute to
     * balances; PAID and VERIFIED are settled and never listed.
     */
    @Query("""
            SELECT d FROM Debt d
            WHERE d.groupId = :groupId
              AND d.status IN (com.amano.backend.groupexpense.DebtStatus.PENDING,
                  com.amano.backend.groupexpense.DebtStatus.PARTIALLY_PAID,
                  com.amano.backend.groupexpense.DebtStatus.PAYMENT_REPORTED,
                  com.amano.backend.groupexpense.DebtStatus.OVERDUE)
              AND (d.debtorUserId = :userId OR d.creditorUserId = :userId)
            ORDER BY d.id
            """)
    List<Debt> findOpenForUserInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 022 D7/T063: idempotent bulk transition PENDING → OVERDUE for the debts
     * of one group whose origin expense is older than {@code before}. The
     * {@code status = PENDING} predicate makes a second execution a no-op and
     * keeps PARTIALLY_PAID, PAYMENT_REPORTED, PAID and VERIFIED untouched.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Debt d
            SET d.status = com.amano.backend.groupexpense.DebtStatus.OVERDUE,
                d.updatedAt = :now
            WHERE d.status = com.amano.backend.groupexpense.DebtStatus.PENDING
              AND d.groupId = :groupId
              AND EXISTS (SELECT 1 FROM GroupExpense e
                          WHERE e.id = d.expenseId AND e.expenseDate < :before)
            """)
    int markPendingOverdue(@Param("groupId") Long groupId, @Param("before") LocalDate before,
            @Param("now") OffsetDateTime now);

    /** True when the user has an unsettled debt of the group as debtor OR creditor (FR-017). */
    @Query("""
            SELECT COUNT(d) > 0 FROM Debt d
            WHERE d.groupId = :groupId
              AND d.status NOT IN (com.amano.backend.groupexpense.DebtStatus.PAID,
                  com.amano.backend.groupexpense.DebtStatus.VERIFIED)
              AND (d.debtorUserId = :userId OR d.creditorUserId = :userId)
            """)
    boolean existsOpenForUser(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** True when the simulated profile has an unsettled debt of the group as debtor (FR-018). */
    @Query("""
            SELECT COUNT(d) > 0 FROM Debt d
            WHERE d.groupId = :groupId
              AND d.status NOT IN (com.amano.backend.groupexpense.DebtStatus.PAID,
                  com.amano.backend.groupexpense.DebtStatus.VERIFIED)
              AND d.debtorSimulatedProfileId = :profileId
            """)
    boolean existsOpenForSimulatedProfile(@Param("groupId") Long groupId, @Param("profileId") Long profileId);
}
