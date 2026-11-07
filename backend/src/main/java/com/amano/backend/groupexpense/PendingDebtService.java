package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupNotFoundException;
import com.amano.backend.groups.GroupRepository;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Read model of the caller's open debts in a group (022 US7, T043-T045). It
 * follows the spec and plan: a member sees the debts in which they participate
 * as debtor or creditor; non-members get the same neutral 404 as the rest of
 * the group endpoints. Only the four states that still contribute to balances
 * are returned, so PAID and VERIFIED never appear.
 */
@Service
public class PendingDebtService {

    static final String NOT_MEMBER = "No existe el grupo solicitado o no sos miembro";

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final DebtRepository debtRepository;
    private final GroupExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public PendingDebtService(GroupRepository groupRepository, GroupMemberRepository groupMemberRepository,
            DebtRepository debtRepository, GroupExpenseRepository expenseRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.debtRepository = debtRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    /** Open debts of {@code callerId} in the group, with their origin details. */
    @Transactional(readOnly = true)
    public List<PendingDebtResponse> pending(Long callerId, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
        requireActiveMember(callerId, groupId);

        List<Debt> debts = debtRepository.findOpenForUserInGroup(groupId, callerId);
        if (debts.isEmpty()) {
            return List.of();
        }

        Set<Long> expenseIds = debts.stream().map(Debt::getExpenseId).collect(Collectors.toSet());
        Map<Long, GroupExpense> expenses = expenseRepository.findAllById(expenseIds).stream()
                .collect(Collectors.toMap(GroupExpense::getId, Function.identity()));

        Set<Long> creditorIds = debts.stream().map(Debt::getCreditorUserId).collect(Collectors.toSet());
        Map<Long, String> creditorNames = new HashMap<>();
        for (User creditor : userRepository.findAllById(creditorIds)) {
            creditorNames.put(creditor.getId(), displayOrEmail(creditor));
        }

        return debts.stream()
                .map(debt -> view(debt, expenses.get(debt.getExpenseId()), group, creditorNames))
                .toList();
    }

    private PendingDebtResponse view(Debt debt, GroupExpense expense, Group group,
            Map<Long, String> creditorNames) {
        BigDecimal convertedAmount = null;
        Currency convertedCurrency = null;
        if (expense != null && expense.getConvertedAmount() != null) {
            if (expense.getCurrency() == group.getPrimaryCurrency()
                    || debt.getCurrency() == group.getPrimaryCurrency()) {
                convertedAmount = money(debt.getAmount());
                convertedCurrency = group.getPrimaryCurrency();
            }
            else if (expense.getExchangeRate() != null) {
                convertedAmount = debt.getAmount().multiply(expense.getExchangeRate())
                        .setScale(2, RoundingMode.HALF_UP);
                convertedCurrency = group.getPrimaryCurrency();
            }
        }

        return new PendingDebtResponse(
                debt.getId(),
                debt.getDebtorName(),
                creditorNames.getOrDefault(debt.getCreditorUserId(), "Usuario " + debt.getCreditorUserId()),
                money(debt.getAmount()),
                money(debt.getPaidAmount()),
                money(debt.getOutstanding()),
                debt.getStatus(),
                debt.getExpenseId(),
                debt.getShareId(),
                debt.getCurrency(),
                convertedAmount,
                convertedCurrency);
    }

    private void requireActiveMember(Long callerId, Long groupId) {
        GroupMember membership = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, callerId)
                .orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
        if (membership.getLeftAt() != null) {
            throw new GroupNotFoundException(NOT_MEMBER);
        }
    }

    private static String displayOrEmail(User user) {
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
