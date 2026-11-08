package com.amano.backend.friendship;

/**
 * Outbound port for the future shared-expenses/debts module: whether two people
 * still have a pending direct debt (a shared expense without a group). Feature
 * 016 blocks deleting a friendship while such a debt exists. Group debts are
 * deliberately outside this port: they never block deleting a friendship.
 *
 * <p>Implemented since 027 by
 * {@code com.amano.backend.groupexpense.DebtDirectDebtAdapter}: the debts of an
 * expense with a friend ({@code group_id} null) are the direct debts.
 */
public interface DirectDebtPort {

    /** True when {@code userId} and {@code friendId} have a pending direct debt. */
    boolean hasPendingDirectDebt(Long userId, Long friendId);
}
