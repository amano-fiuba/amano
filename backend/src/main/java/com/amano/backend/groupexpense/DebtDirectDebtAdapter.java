package com.amano.backend.groupexpense;

import org.springframework.stereotype.Component;

import com.amano.backend.friendship.DirectDebtPort;

/**
 * 027 US4: the real {@link DirectDebtPort} of 016. A direct debt is an unsettled
 * debt of an expense with a friend ({@code group_id} null) between the two people,
 * in either direction. Group debts never count.
 */
@Component
public class DebtDirectDebtAdapter implements DirectDebtPort {

    private final DebtRepository debtRepository;

    public DebtDirectDebtAdapter(DebtRepository debtRepository) {
        this.debtRepository = debtRepository;
    }

    @Override
    public boolean hasPendingDirectDebt(Long userId, Long friendId) {
        return debtRepository.existsOpenDirectBetween(userId, friendId);
    }
}
