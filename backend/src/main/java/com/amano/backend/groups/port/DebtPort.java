package com.amano.backend.groups.port;

/**
 * Outbound port for the debts module: whether a participant of a group still
 * has debts that are not settled. Used by the leave/removal guards (010
 * US4/US5). Registered users and simulated profiles live in different id
 * spaces ({@code users} vs {@code simulated_profiles}), so each kind has its
 * own question (017 research.md D7): asking with a profile id as if it were a
 * user id would mix both.
 */
public interface DebtPort {

    /** True when {@code userId} has unsettled debts in the group, as debtor or creditor. */
    boolean hasPendingDebtsForUser(Long groupId, Long userId);

    /** True when the simulated profile {@code profileId} has unsettled debts in the group as debtor. */
    boolean hasPendingDebtsForSimulatedProfile(Long groupId, Long profileId);
}
