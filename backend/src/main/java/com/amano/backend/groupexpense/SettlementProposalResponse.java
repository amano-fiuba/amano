package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.util.List;

import com.amano.backend.user.Currency;

/**
 * Response of {@code GET /api/groups/{id}/settlement-proposal} (022 T027).
 * The proposal is advisory and read-only: it never creates payments or changes
 * debts. Balances are grouped by debt currency, so there is one proposal per
 * currency and amounts in different currencies are never added together.
 *
 * <p>A transfer involving a simulated profile is marked {@code informative}:
 * the group can use it to settle the balance, but the simulated person has no
 * account to execute it (spec Q6).
 */
public record SettlementProposalResponse(
        Long groupId,
        Currency primaryCurrency,
        List<CurrencyProposal> proposals) {

    /** Greedy transfers of one currency and their informational totals. */
    public record CurrencyProposal(
            Currency currency,
            List<TransferView> transfers,
            TransferSummary summary) {
    }

    public record TransferView(
            Long fromMemberId,
            String fromMemberName,
            ParticipantType fromMemberType,
            Long toMemberId,
            String toMemberName,
            ParticipantType toMemberType,
            BigDecimal amount,
            boolean informative) {
    }

    public record TransferSummary(
            BigDecimal totalTransferred,
            BigDecimal totalNetBalances,
            int membersSettled,
            int transferCount) {
    }
}
