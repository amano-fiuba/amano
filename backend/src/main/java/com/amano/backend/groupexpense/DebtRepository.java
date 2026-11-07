package com.amano.backend.groupexpense;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** True when the user has an unsettled debt of the group as debtor OR creditor (FR-017). */
    @Query("""
            SELECT COUNT(d) > 0 FROM Debt d
            WHERE d.groupId = :groupId
              AND d.status <> com.amano.backend.groupexpense.DebtStatus.PAID
              AND (d.debtorUserId = :userId OR d.creditorUserId = :userId)
            """)
    boolean existsOpenForUser(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** True when the simulated profile has an unsettled debt of the group as debtor (FR-018). */
    @Query("""
            SELECT COUNT(d) > 0 FROM Debt d
            WHERE d.groupId = :groupId
              AND d.status <> com.amano.backend.groupexpense.DebtStatus.PAID
              AND d.debtorSimulatedProfileId = :profileId
            """)
    boolean existsOpenForSimulatedProfile(@Param("groupId") Long groupId, @Param("profileId") Long profileId);
}
