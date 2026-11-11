package com.amano.backend.groups.port;

import org.springframework.stereotype.Component;

import com.amano.backend.groupexpense.DebtRepository;

/**
 * Real {@link DebtPort} (017 US2, research.md D7) over the {@code debts} table,
 * replacing the 010 stub. A person is blocked by ANY unsettled debt (PENDING
 * or PARTIALLY_PAID) of the group: a user as debtor or creditor, a simulated
 * profile as debtor (it can never be a creditor: the payer is always a
 * registered member). This is the 017 rule (spec Clarifications 2026-11-05),
 * which overrides the "saldos netos" assumption of 010: no netting between
 * crossed debts. Same pattern as {@link AcceptedFriendshipPort}: the port lives
 * in {@code groups}, the data in the other module.
 */
@Component
public class LedgerDebtPort implements DebtPort {

    private final DebtRepository debtRepository;

    public LedgerDebtPort(DebtRepository debtRepository) {
        this.debtRepository = debtRepository;
    }

    @Override
    public boolean hasPendingDebtsForUser(Long groupId, Long userId) {
        return debtRepository.existsOpenForUser(groupId, userId);
    }

    @Override
    public boolean hasPendingDebtsForSimulatedProfile(Long groupId, Long profileId) {
        return debtRepository.existsOpenForSimulatedProfile(groupId, profileId);
    }
}
