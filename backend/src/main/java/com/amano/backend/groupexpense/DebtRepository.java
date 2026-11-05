package com.amano.backend.groupexpense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence of {@link Debt} (017). The two {@code existsOpen…} queries back
 * the real {@code DebtPort} (research.md D7): "open" means not PAID (PENDING
 * or PARTIALLY_PAID), served by the partial indexes {@code ix_debts_open_*} of
 * V13.
 */
public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByExpenseId(Long expenseId);

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
