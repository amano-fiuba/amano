package com.amano.backend.friendship;

import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link DirectDebtPort} until the shared-expenses/debts
 * module exists: it always reports no direct debt, so deleting a friendship is
 * not blocked by this rule yet. The blocking path is covered by unit tests with
 * a mocked port (feature 016, research.md D2).
 */
@Component
public class NoOpDirectDebtPort implements DirectDebtPort {

    @Override
    public boolean hasPendingDirectDebt(Long userId, Long friendId) {
        return false;
    }
}
