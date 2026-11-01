package com.amano.backend.groups.port;

import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link DebtPort} until the debts module exists:
 * always reports zero pending debts so local development and tests can remove
 * members freely.
 */
@Component
public class NoOpDebtPort implements DebtPort {

    @Override
    public boolean hasPendingDebts(Long groupId, Long userId) {
        return false;
    }
}
