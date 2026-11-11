package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.util.List;

import com.amano.backend.groupexpense.ParticipantType;
import com.amano.backend.user.Currency;

/**
 * Response of {@code GET /api/groups/{id}/balances} (022 T012). Balances are
 * grouped by debt currency so legacy debts in different currencies are never
 * added together. Every {@link CurrencyBalances#total()} is zero.
 */
public record GroupBalancesResponse(
        Long groupId,
        Currency primaryCurrency,
        List<CurrencyBalances> currencies) {

    public record CurrencyBalances(
            Currency currency,
            BigDecimal total,
            List<MemberBalance> balances) {
    }

    public record MemberBalance(
            Long memberId,
            String memberName,
            ParticipantType memberType,
            BigDecimal netBalance) {
    }
}
