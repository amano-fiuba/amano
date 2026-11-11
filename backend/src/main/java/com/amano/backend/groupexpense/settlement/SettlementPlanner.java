package com.amano.backend.groupexpense.settlement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.amano.backend.groupexpense.balance.MemberRef;

/**
 * Pure greedy settlement planner (022 D5). It receives the net balances of one
 * currency (positive = the group owes them, negative = they owe the group) and
 * proposes transfers that leave every member at zero.
 *
 * <p>At each step it takes the largest creditor and the largest debtor, with
 * {@link MemberRef} as a deterministic tie-breaker, and transfers the smaller
 * of the two amounts. Every step zeroes at least one member, so a proposal has
 * at most {@code n - 1} transfers for {@code n} non-zero balances. The absolute
 * minimum is a set-cover problem and is deliberately not searched.
 *
 * <p>The input map is never modified. The total transferred equals the sum of
 * the positive net balances, not the gross debt.
 */
public final class SettlementPlanner {

    private SettlementPlanner() {
    }

    /**
     * Transfers that settle the given balances, in a stable order.
     *
     * @throws IllegalArgumentException if the map or one of its values is null,
     *                                  or if the balances do not sum to zero
     */
    public static List<Transfer> plan(Map<MemberRef, BigDecimal> netBalances) {
        Objects.requireNonNull(netBalances, "Los saldos son obligatorios");
        Map<MemberRef, BigDecimal> balances = new TreeMap<>();
        for (Map.Entry<MemberRef, BigDecimal> entry : netBalances.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "El miembro del saldo es obligatorio");
            Objects.requireNonNull(entry.getValue(), "El saldo del miembro es obligatorio");
            if (entry.getValue().signum() != 0) {
                balances.put(entry.getKey(), entry.getValue());
            }
        }
        BigDecimal total = balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() != 0) {
            throw new IllegalArgumentException("Los saldos deben sumar cero");
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!balances.isEmpty()) {
            MemberRef creditor = null;
            BigDecimal credit = null;
            MemberRef debtor = null;
            BigDecimal debt = null;
            for (Map.Entry<MemberRef, BigDecimal> entry : balances.entrySet()) {
                BigDecimal value = entry.getValue();
                if (value.signum() > 0 && (credit == null || value.compareTo(credit) > 0)) {
                    creditor = entry.getKey();
                    credit = value;
                }
                if (value.signum() < 0 && (debt == null || value.compareTo(debt) < 0)) {
                    debtor = entry.getKey();
                    debt = value;
                }
            }
            if (creditor == null || debtor == null) {
                throw new IllegalStateException("Los saldos no se pueden liquidar");
            }
            BigDecimal amount = credit.min(debt.negate());
            transfers.add(new Transfer(debtor, creditor, amount));

            BigDecimal remainingDebt = debt.add(amount);
            BigDecimal remainingCredit = credit.subtract(amount);
            if (remainingDebt.signum() == 0) {
                balances.remove(debtor);
            }
            else {
                balances.put(debtor, remainingDebt);
            }
            if (remainingCredit.signum() == 0) {
                balances.remove(creditor);
            }
            else {
                balances.put(creditor, remainingCredit);
            }
        }
        return List.copyOf(transfers);
    }
}
