package com.amano.backend.groupexpense.balance;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.ParticipantType;
import com.amano.backend.user.Currency;

/**
 * Pure net-balance calculation (022 D4). There is no balances table: every
 * group balance is derived from the debts that are still unsettled.
 *
 * <p>{@code PAID} and {@code VERIFIED} debts are settled and ignored;
 * {@code PENDING}, {@code PARTIALLY_PAID}, {@code PAYMENT_REPORTED} and
 * {@code OVERDUE} contribute {@link Debt#getOutstanding()}. The creditor gains
 * the outstanding amount and the debtor loses it, so the sum of all balances is
 * always zero.
 *
 * <p>Balances are grouped by the debt currency: legacy data can contain debts
 * in more than one currency and adding them numerically would be meaningless.
 * Each currency has its own zero-sum balance map. Simulated debtors participate
 * as their own {@link MemberRef}.
 *
 * <p>The result is immutable, ordered by {@link Currency} and then by
 * {@link MemberRef}; the method has no Spring or JPA dependency, so it is
 * unit-testable in isolation.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    /**
     * Net balance per member and currency: positive means the group owes them,
     * negative means they owe the group. Every inner map sums to zero.
     *
     * @throws IllegalArgumentException if the collection is null, a debt is null
     *                                  or a debt lacks status/currency
     */
    public static Map<Currency, Map<MemberRef, BigDecimal>> calculate(Collection<Debt> debts) {
        if (debts == null) {
            throw new IllegalArgumentException("La lista de deudas es obligatoria");
        }
        Map<Currency, Map<MemberRef, BigDecimal>> balancesByCurrency = new TreeMap<>();
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
            if (debt.getCurrency() == null) {
                throw new IllegalArgumentException("La deuda no tiene moneda");
            }
            BigDecimal outstanding = debt.getOutstanding();
            if (outstanding == null || outstanding.signum() == 0) {
                continue;
            }
            Map<MemberRef, BigDecimal> balances =
                    balancesByCurrency.computeIfAbsent(debt.getCurrency(), currency -> new TreeMap<>());
            MemberRef debtor = debtorOf(debt);
            MemberRef creditor = MemberRef.registered(debt.getCreditorUserId());
            balances.merge(debtor, outstanding.negate(), BigDecimal::add);
            balances.merge(creditor, outstanding, BigDecimal::add);
        }
        Map<Currency, Map<MemberRef, BigDecimal>> immutable = new TreeMap<>();
        balancesByCurrency.forEach((currency, balances) ->
                immutable.put(currency, Collections.unmodifiableMap(balances)));
        return Collections.unmodifiableMap(immutable);
    }

    private static MemberRef debtorOf(Debt debt) {
        if (debt.getDebtorType() == ParticipantType.SIMULATED) {
            return MemberRef.simulated(debt.getDebtorSimulatedProfileId());
        }
        return MemberRef.registered(debt.getDebtorUserId());
    }
}
