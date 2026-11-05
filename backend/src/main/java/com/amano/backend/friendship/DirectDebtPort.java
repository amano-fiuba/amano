package com.amano.backend.friendship;

/**
 * Outbound port for the future shared-expenses/debts module: whether two people
 * still have a pending direct debt (a shared expense without a group). Feature
 * 016 blocks deleting a friendship while such a debt exists. Group debts are
 * deliberately outside this port: they never block deleting a friendship.
 *
 * <p>The module does not exist yet, so {@link NoOpDirectDebtPort} is the current
 * implementation. The real module will replace it without changing the port or
 * the friends feature.
 */
public interface DirectDebtPort {

    /** True when {@code userId} and {@code friendId} have a pending direct debt. */
    boolean hasPendingDirectDebt(Long userId, Long friendId);
}
