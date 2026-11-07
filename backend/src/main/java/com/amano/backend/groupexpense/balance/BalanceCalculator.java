package com.amano.backend.groupexpense.balance;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.ParticipantType;

/**
 * Pure net-balance calculation (022 D4). There is no balances table: every
 * group balance is derived from the debts that are still unsettled.
 *
 * <p>{@code PAID} and {@code VERIFIED} debts are settled and ignored;
 * {@code PENDING}, {@code PARTIALLY_PAID}, {@code PAYMENT_REPORTED} and
 * {@code OVERDUE} contribute {@link Debt#getOutstanding()}. The creditor gains
 * the outstanding amount and the debtor loses it, so the sum of all balances is
 * always zero. Simulated debtors participate as their own {@link MemberRef}.
 *
 * <p>The result is an immutable map ordered by {@link MemberRef}; the method has
 * no Spring or JPA dependency, so it is unit-testable in isolation.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    /**
     * Net balance per member: positive means the group owes them, negative means
     * they owe the group.
     *
     * @throws IllegalArgumentException if a debt is null or lacks its status or ids
     */
    public static Map<MemberRef, BigDecimal> calculate(Collection<Debt> debts) {
        if (debts == null) {
            throw new IllegalArgumentException("La lista de deudas es obligatoria");
        }
        Map<MemberRef, BigDecimal> balances = new TreeMap<>();
        for (Debt debt : debts) {
            if (debt == null) {
                throw new IllegalArgumentException("La lista de deudas no puede contener nulos");
            }
            if (debt.getStatus() == null) {
                throw new IllegalArgumentException("La deuda no tiene estado");
            }
            if (debt.getStatus().isSettled()) {
                continue;
            }
            BigDecimal outstanding = debt.getOutstanding();
            if (outstanding == null || outstanding.signum() == 0) {
                continue;
            }
            MemberRef debtor = debtorOf(debt);
            MemberRef creditor = MemberRef.registered(debt.getCreditorUserId());
            balances.merge(debtor, outstanding.negate(), BigDecimal::add);
            balances.merge(creditor, outstanding, BigDecimal::add);
        }
        return Collections.unmodifiableMap(balances);
    }

    private static MemberRef debtorOf(Debt debt) {
        if (debt.getDebtorType() == ParticipantType.SIMULATED) {
            return MemberRef.simulated(debt.getDebtorSimulatedProfileId());
        }
        return MemberRef.registered(debt.getDebtorUserId());
    }
}
