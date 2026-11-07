package com.amano.backend.groupexpense.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.amano.backend.groupexpense.balance.MemberRef;

/**
 * 022 T015/D5: the greedy proposal is reproducible, read-only, has at most
 * {@code n - 1} transfers and moves exactly the sum of the positive net
 * balances (not the gross debt).
 */
class SettlementPlannerTest {

    private static final long SEED = 20261106L;

    private static final MemberRef ANA = MemberRef.registered(7L);
    private static final MemberRef JUAN = MemberRef.registered(15L);
    private static final MemberRef CARLA = MemberRef.registered(16L);
    private static final MemberRef ABUELA = MemberRef.simulated(4L);
    private static final MemberRef ALICE = MemberRef.registered(17L);
    private static final MemberRef BOB = MemberRef.registered(18L);
    private static final MemberRef CLAIRE = MemberRef.registered(19L);

    private static Map<MemberRef, BigDecimal> balances(String... values) {
        Map<MemberRef, BigDecimal> map = new HashMap<>();
        MemberRef[] members = { ANA, JUAN, CARLA, ABUELA };
        for (int i = 0; i < values.length; i++) {
            map.put(members[i], new BigDecimal(values[i]));
        }
        return map;
    }

    private static BigDecimal positiveTotal(Map<MemberRef, BigDecimal> balances) {
        return balances.values().stream()
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<MemberRef, BigDecimal> apply(Map<MemberRef, BigDecimal> balances,
            List<Transfer> transfers) {
        Map<MemberRef, BigDecimal> after = new HashMap<>(balances);
        for (Transfer transfer : transfers) {
            after.merge(transfer.from(), transfer.amount(), BigDecimal::add);
            after.merge(transfer.to(), transfer.amount().negate(), BigDecimal::add);
        }
        after.replaceAll((member, value) -> value.stripTrailingZeros().signum() == 0
                ? BigDecimal.ZERO.setScale(2)
                : value);
        return after;
    }

    @Test
    void alreadyBalancedMembersNeedNoTransfers() {
        assertThat(SettlementPlanner.plan(Map.of())).isEmpty();
        assertThat(SettlementPlanner.plan(balances("0.00", "0.00", "0.00", "0.00"))).isEmpty();
    }

    @Test
    void aSingleDebtorPaysTheSingleCreditor() {
        List<Transfer> transfers = SettlementPlanner.plan(balances("100.00", "-100.00", "0.00", "0.00"));

        assertThat(transfers).containsExactly(new Transfer(JUAN, ANA, new BigDecimal("100.00")));
    }

    @Test
    void crossDebtsAreCompensatedInsteadOfPayingTheGrossDebt() {
        // Ana is owed 100 by Juan and 100 by Carla, but Juan and Carla also owe
        // each other: the net total moved must be the positive sum, not the gross.
        Map<MemberRef, BigDecimal> net = balances("150.00", "-20.00", "-130.00", "0.00");
        List<Transfer> transfers = SettlementPlanner.plan(net);

        assertThat(transfers).hasSizeLessThanOrEqualTo(3);
        assertThat(transfers.stream().map(Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(positiveTotal(net));
        assertThat(apply(net, transfers).values()).allMatch(value -> value.signum() == 0);
    }

    /** T030: the canonical example from the plan, with an exact proposal. */
    @Test
    void theCanonicalExampleProposesTwoTransfersForThreeMembers() {
        Map<MemberRef, BigDecimal> net = Map.of(
                ALICE, new BigDecimal("-7.00"),
                BOB, new BigDecimal("5.00"),
                CLAIRE, new BigDecimal("2.00"));

        List<Transfer> transfers = SettlementPlanner.plan(net);

        assertThat(transfers).containsExactly(
                new Transfer(ALICE, BOB, new BigDecimal("5.00")),
                new Transfer(ALICE, CLAIRE, new BigDecimal("2.00")));
        assertThat(transfers.stream().map(Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("7.00");
        assertThat(transfers).hasSize(2).hasSize(3 - 1);
    }

    /** T015: applying the liquidation leaves every balance at zero, so the sum is zero. */
    @Test
    void applyingTheLiquidationLeavesEveryBalanceAtZero() {
        Map<MemberRef, BigDecimal> net = balances("150.00", "-20.00", "-130.00", "0.00");

        Map<MemberRef, BigDecimal> after = apply(net, SettlementPlanner.plan(net));

        assertThat(after.values()).allMatch(value -> value.signum() == 0);
        assertThat(after.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0.00");
    }

    /** T031: planning is a pure function; it never mutates its input. */
    @Test
    void thePlanDoesNotModifyTheInputBalances() {
        Map<MemberRef, BigDecimal> net = balances("150.00", "-20.00", "-130.00", "0.00");
        Map<MemberRef, BigDecimal> before = new HashMap<>(net);

        SettlementPlanner.plan(net);

        assertThat(net).isEqualTo(before);
    }

    @Test
    void everyGeneratedSettlementZeroesAllBalancesWithAtMostNMinusOneTransfers() {
        Random random = new Random(SEED);

        for (int round = 0; round < 300; round++) {
            Map<MemberRef, BigDecimal> net = generatedZeroSumBalances(random);
            Map<MemberRef, BigDecimal> before = new HashMap<>(net);
            long nonZero = net.values().stream().filter(value -> value.signum() != 0).count();

            List<Transfer> transfers = SettlementPlanner.plan(net);

            assertThat(transfers).isEqualTo(SettlementPlanner.plan(net));
            assertThat(net).isEqualTo(before);
            assertThat(transfers).hasSizeLessThanOrEqualTo((int) Math.max(0, nonZero - 1));
            assertThat(transfers.stream().map(Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(positiveTotal(net));
            assertThat(apply(net, transfers).values()).allMatch(value -> value.signum() == 0);
        }
    }

    /**
     * Builds random balances that sum to zero by construction: every generated
     * debt gives {@code amount} to one member and takes it from another.
     */
    private static Map<MemberRef, BigDecimal> generatedZeroSumBalances(Random random) {
        MemberRef[] members = { ANA, JUAN, CARLA, ABUELA };
        BigDecimal[] values = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        int operations = random.nextInt(1, 15);
        for (int i = 0; i < operations; i++) {
            int creditor = random.nextInt(members.length);
            int debtor = random.nextInt(members.length);
            if (creditor == debtor) {
                continue;
            }
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 100_000), 2);
            values[creditor] = values[creditor].add(amount);
            values[debtor] = values[debtor].subtract(amount);
        }
        Map<MemberRef, BigDecimal> net = new HashMap<>();
        for (int i = 0; i < members.length; i++) {
            net.put(members[i], values[i].setScale(2, RoundingMode.HALF_UP));
        }
        return net;
    }

    @Test
    void balancesThatDoNotSumToZeroAreRejected() {
        assertThatThrownBy(() -> SettlementPlanner.plan(balances("10.00", "-5.00", "0.00", "0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los saldos deben sumar cero");
        assertThatThrownBy(() -> SettlementPlanner.plan(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aTransferCannotBeEmptyOrGoToTheSameMember() {
        assertThatThrownBy(() -> new Transfer(ANA, ANA, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transfer(ANA, JUAN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transfer(ANA, JUAN, new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transfer(null, JUAN, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theProposalIsInmutable() {
        List<Transfer> transfers = SettlementPlanner.plan(balances("100.00", "-100.00", "0.00", "0.00"));

        assertThatThrownBy(() -> transfers.add(new Transfer(ANA, JUAN, BigDecimal.ONE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
