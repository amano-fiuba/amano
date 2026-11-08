package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.balance.BalanceCalculator;
import com.amano.backend.groupexpense.balance.MemberRef;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupNotFoundException;
import com.amano.backend.groups.GroupRepository;
import com.amano.backend.groups.SimulatedProfile;
import com.amano.backend.groups.SimulatedProfileRepository;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Derived group balances (022 T012, D4). Reads the group debts, delegates the
 * pure calculation to {@link BalanceCalculator} and enriches the result with
 * the visible member names. It never writes.
 */
@Service
public class GroupBalanceService {

    static final String NOT_MEMBER = "No existe el grupo solicitado o no sos miembro";

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SimulatedProfileRepository simulatedProfileRepository;
    private final UserRepository userRepository;
    private final DebtRepository debtRepository;

    public GroupBalanceService(GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            SimulatedProfileRepository simulatedProfileRepository,
            UserRepository userRepository,
            DebtRepository debtRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.simulatedProfileRepository = simulatedProfileRepository;
        this.userRepository = userRepository;
        this.debtRepository = debtRepository;
    }

    /** Balances per currency of a group the caller belongs to (neutral 404 otherwise). */
    @Transactional(readOnly = true)
    public GroupBalancesResponse balances(Long callerId, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
        requireActiveMember(callerId, groupId);

        List<Debt> debts = debtRepository.findByGroupId(groupId);
        Map<Currency, Map<MemberRef, BigDecimal>> byCurrency = BalanceCalculator.calculate(debts);
        Map<MemberRef, String> names = memberNames(groupId, byCurrency);

        TreeSet<Currency> currencies = debts.stream()
                .map(Debt::getCurrency)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        if (currencies.isEmpty()) {
            currencies.add(group.getPrimaryCurrency());
        }

        List<GroupBalancesResponse.CurrencyBalances> views = currencies.stream()
                .map(currency -> currencyView(currency, byCurrency.getOrDefault(currency, Map.of()), names))
                .toList();
        return new GroupBalancesResponse(groupId, group.getPrimaryCurrency(), views);
    }

    private void requireActiveMember(Long callerId, Long groupId) {
        GroupMember membership = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, callerId)
                .orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
        if (membership.getLeftAt() != null) {
            throw new GroupNotFoundException(NOT_MEMBER);
        }
    }

    private GroupBalancesResponse.CurrencyBalances currencyView(Currency currency,
            Map<MemberRef, BigDecimal> balances, Map<MemberRef, String> names) {
        List<GroupBalancesResponse.MemberBalance> members = names.entrySet().stream()
                .map(entry -> new GroupBalancesResponse.MemberBalance(
                        entry.getKey().id(),
                        entry.getValue(),
                        entry.getKey().type(),
                        money(balances.getOrDefault(entry.getKey(), BigDecimal.ZERO))))
                .toList();
        BigDecimal total = money(balances.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new GroupBalancesResponse.CurrencyBalances(currency, total, members);
    }

    /**
     * Names of the active members plus anyone that still appears in a debt
     * (for example a member that left before the pending-balance guard).
     */
    private Map<MemberRef, String> memberNames(Long groupId,
            Map<Currency, Map<MemberRef, BigDecimal>> byCurrency) {
        Set<Long> registeredIds = groupMemberRepository.findByIdGroupIdAndLeftAtIsNull(groupId).stream()
                .map(membership -> membership.getId().getUserId())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<Long> simulatedIds = simulatedProfileRepository.findByGroupId(groupId).stream()
                .map(SimulatedProfile::getId)
                .collect(Collectors.toCollection(TreeSet::new));
        for (Map<MemberRef, BigDecimal> balances : byCurrency.values()) {
            for (MemberRef ref : balances.keySet()) {
                if (ref.isSimulated()) {
                    simulatedIds.add(ref.id());
                }
                else {
                    registeredIds.add(ref.id());
                }
            }
        }

        Map<Long, User> users = new HashMap<>();
        for (User user : userRepository.findAllById(registeredIds)) {
            users.put(user.getId(), user);
        }
        Map<Long, SimulatedProfile> simulated = new HashMap<>();
        for (SimulatedProfile profile : simulatedProfileRepository.findAllById(simulatedIds)) {
            simulated.put(profile.getId(), profile);
        }

        Map<MemberRef, String> names = new TreeMap<>();
        for (Long userId : registeredIds) {
            User user = users.get(userId);
            names.put(MemberRef.registered(userId), user == null ? "Usuario " + userId : displayOrEmail(user));
        }
        for (Long profileId : simulatedIds) {
            SimulatedProfile profile = simulated.get(profileId);
            names.put(MemberRef.simulated(profileId),
                    profile == null ? "Perfil simulado " + profileId : profile.getName());
        }
        return names;
    }

    private static String displayOrEmail(User user) {
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
