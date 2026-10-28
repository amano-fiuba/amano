package com.amano.backend.groups.port;

/**
 * Outbound port for the (not yet existing) debts module: whether a member of a
 * group still has pending debts with the group's other members. Used by the
 * last-member removal/leave guards (US4/US5). No implementation exists yet, so
 * {@link NoOpDebtPort} always reports zero debts.
 */
public interface DebtPort {

    /** True when {@code userId} has pending debts with other members of the group. */
    boolean hasPendingDebts(Long groupId, Long userId);
}
