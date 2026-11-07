package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.balance.MemberRef;
import com.amano.backend.groupexpense.settlement.SettlementPlanner;
import com.amano.backend.groupexpense.settlement.Transfer;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.user.Currency;

/**
 * Settlement proposal (022 T027, T048, D5). It reuses the derived balances of
 * {@link GroupBalanceService} and runs the pure {@link SettlementPlanner} on
 * each currency separately, so amounts in different currencies are never added.
 *
 * <p>The proposal does not create payments nor change any debt or expense; it
 * records {@code LIQUIDATION_PROPOSED} in the group activity history at most
 * once per group and day (T048). A transfer that involves a simulated profile
 * is marked as informative because that person has no account to execute it.
 */
@Service
public class GroupSettlementService {

    private final GroupBalanceService balanceService;
    private final ActivityEventService activityEventService;
    private final Clock clock;

    public GroupSettlementService(GroupBalanceService balanceService,
            ActivityEventService activityEventService, Clock clock) {
        this.balanceService = balanceService;
        this.activityEventService = activityEventService;
        this.clock = clock;
    }

    /** One proposal per currency with a non-zero balance (neutral 404 for non-members). */
    @Transactional
    public SettlementProposalResponse proposal(Long callerId, Long groupId) {
        GroupBalancesResponse balances = balanceService.balances(callerId, groupId);

        List<SettlementProposalResponse.CurrencyProposal> proposals = new ArrayList<>();
        int transferCount = 0;
        for (GroupBalancesResponse.CurrencyBalances currencyBalances : balances.currencies()) {
            SettlementProposalResponse.CurrencyProposal proposal =
                    proposalOf(currencyBalances.currency(), currencyBalances.balances());
            if (proposal != null) {
                proposals.add(proposal);
                transferCount += proposal.summary().transferCount();
            }
        }
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime dayStart = today.atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime dayEnd = today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
        activityEventService.recordLiquidationProposed(
                groupId, callerId, transferCount, balances.primaryCurrency().name(), dayStart, dayEnd);
        return new SettlementProposalResponse(groupId, balances.primaryCurrency(), List.copyOf(proposals));
    }

    private static SettlementProposalResponse.CurrencyProposal proposalOf(Currency currency,
            List<GroupBalancesResponse.MemberBalance> memberBalances) {
        Map<MemberRef, BigDecimal> netBalances = new HashMap<>();
        Map<MemberRef, GroupBalancesResponse.MemberBalance> members = new HashMap<>();
        for (GroupBalancesResponse.MemberBalance member : memberBalances) {
            MemberRef ref = new MemberRef(member.memberType(), member.memberId());
            members.put(ref, member);
            if (member.netBalance().signum() != 0) {
                netBalances.put(ref, member.netBalance());
            }
        }
        if (netBalances.isEmpty()) {
            return null;
        }

        List<Transfer> transfers = SettlementPlanner.plan(netBalances);
        List<SettlementProposalResponse.TransferView> views = transfers.stream()
                .map(transfer -> view(transfer, members))
                .toList();

        BigDecimal totalTransferred = transfers.stream()
                .map(Transfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNetBalances = netBalances.values().stream()
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        SettlementProposalResponse.TransferSummary summary =
                new SettlementProposalResponse.TransferSummary(
                        money(totalTransferred),
                        money(totalNetBalances),
                        netBalances.size(),
                        views.size());
        return new SettlementProposalResponse.CurrencyProposal(currency, views, summary);
    }

    private static SettlementProposalResponse.TransferView view(Transfer transfer,
            Map<MemberRef, GroupBalancesResponse.MemberBalance> members) {
        GroupBalancesResponse.MemberBalance from = members.get(transfer.from());
        GroupBalancesResponse.MemberBalance to = members.get(transfer.to());
        boolean informative = transfer.from().isSimulated() || transfer.to().isSimulated();
        return new SettlementProposalResponse.TransferView(
                transfer.from().id(),
                from.memberName(),
                transfer.from().type(),
                transfer.to().id(),
                to.memberName(),
                transfer.to().type(),
                money(transfer.amount()),
                informative);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
