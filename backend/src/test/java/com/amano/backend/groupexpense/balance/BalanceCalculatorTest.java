package com.amano.backend.groupexpense.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseShare;
import com.amano.backend.user.Currency;

/**
 * 022 T009/T013 (D4): the balance is derived from the outstanding debts,
 * grouped by debt currency, includes simulated members and always sums to zero
 * per currency.
 */
class BalanceCalculatorTest {

    private static final long ANA = 7L;
    private static final long JUAN = 15L;
    private static final long CARLA = 16L;
    private static final long ABUELA = 4L;
    private static final long TIO = 5L;

    /** Fixed seed: the property tests are deterministic and reproducible. */
    private static final long SEED = 20261106L;

    private static GroupExpense expense() {
        GroupExpense expense = new GroupExpense();
        ReflectionTestUtils.setField(expense, "id", 12L);
        expense.setGroupId(3L);
        expense.setCreatedByUserId(ANA);
        expense.setPayerUserId(ANA);
        expense.setAmount(new BigDecimal("10000.00"));
        expense.setCurrency(Currency.ARS);
        expense.setExpenseDate(LocalDate.of(2026, 11, 1));
        expense.setIdempotencyKey(UUID.randomUUID());
        return expense;
    }

    private static Debt registeredDebt(long shareId, long debtor, String amount) {
        GroupExpenseShare share = GroupExpenseShare.registered(12L, 1, debtor, "Deudor",
                new BigDecimal(amount));
        ReflectionTestUtils.setField(share, "id", shareId);
        Debt debt = Debt.pendingFor(expense(), share);
        ReflectionTestUtils.setField(debt, "id", shareId);
        return debt;
    }

    private static Debt simulatedDebt(long shareId, long profile, String amount) {
        GroupExpenseShare share = GroupExpenseShare.simulated(12L, 2, profile, "Abuela",
                new BigDecimal(amount));
        ReflectionTestUtils.setField(share, "id", shareId);
        Debt debt = Debt.pendingFor(expense(), share);
        ReflectionTestUtils.setField(debt, "id", shareId);
        return debt;
    }

    private static Debt withCurrency(Debt debt, Currency currency) {
        ReflectionTestUtils.setField(debt, "currency", currency);
        return debt;
    }

    private static Debt withStatus(Debt debt, DebtStatus status) {
        ReflectionTestUtils.setField(debt, "status", status);
        if (status == DebtStatus.PAID || status == DebtStatus.VERIFIED) {
            ReflectionTestUtils.setField(debt, "paidAmount", debt.getAmount());
        }
        else if (status == DebtStatus.PARTIALLY_PAID) {
            ReflectionTestUtils.setField(debt, "paidAmount",
                    debt.getAmount().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
        }
        return debt;
    }

    private static Map<MemberRef, BigDecimal> only(
            Map<Currency, Map<MemberRef, BigDecimal>> balances, Currency currency) {
        assertThat(balances).containsKey(currency);
        return balances.get(currency);
    }

    private static void sumIsZero(Map<MemberRef, BigDecimal> balances) {
        BigDecimal total = balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    void anEmptyGroupHasNoBalances() {
        assertThat(BalanceCalculator.calculate(List.of())).isEmpty();
    }

    @Test
    void theCreditorGainsAndTheDebtorLosesTheOutstandingAmount() {
        Map<MemberRef, BigDecimal> balances = only(
                BalanceCalculator.calculate(List.of(registeredDebt(41L, JUAN, "3333.33"))), Currency.ARS);

        assertThat(balances.get(MemberRef.registered(JUAN))).isEqualByComparingTo("-3333.33");
        assertThat(balances.get(MemberRef.registered(ANA))).isEqualByComparingTo("3333.33");
        sumIsZero(balances);
    }

    @Test
    void aSimulatedDebtorIsADifferentMemberFromARegisteredUserWithTheSameId() {
        Map<MemberRef, BigDecimal> balances = only(
                BalanceCalculator.calculate(List.of(simulatedDebt(42L, ABUELA, "1000.00"))), Currency.ARS);

        assertThat(balances.get(MemberRef.simulated(ABUELA))).isEqualByComparingTo("-1000.00");
        assertThat(balances).doesNotContainKey(MemberRef.registered(ABUELA));
        sumIsZero(balances);
    }

    @Test
    void aPartialPaymentOnlyContributesTheRemainingBalance() {
        Debt debt = registeredDebt(41L, JUAN, "3333.33");
        debt.applyPayment(new BigDecimal("1000.00"));

        Map<MemberRef, BigDecimal> balances =
                only(BalanceCalculator.calculate(List.of(debt)), Currency.ARS);

        assertThat(balances.get(MemberRef.registered(JUAN))).isEqualByComparingTo("-2333.33");
        assertThat(balances.get(MemberRef.registered(ANA))).isEqualByComparingTo("2333.33");
        sumIsZero(balances);
    }

    @Test
    void settledDebtsDoNotContribute() {
        Debt paid = registeredDebt(41L, JUAN, "1000.00");
        paid.applyPayment(new BigDecimal("1000.00"));

        Debt verified = withStatus(registeredDebt(42L, JUAN, "500.00"), DebtStatus.VERIFIED);

        assertThat(BalanceCalculator.calculate(List.of(paid, verified))).isEmpty();
    }

    @Test
    void reportedAndOverdueDebtsStillCount() {
        Debt reported = withStatus(registeredDebt(41L, JUAN, "1000.00"), DebtStatus.PAYMENT_REPORTED);
        Debt overdue = withStatus(registeredDebt(42L, JUAN, "500.00"), DebtStatus.OVERDUE);

        Map<MemberRef, BigDecimal> balances =
                only(BalanceCalculator.calculate(List.of(reported, overdue)), Currency.ARS);

        assertThat(balances.get(MemberRef.registered(JUAN))).isEqualByComparingTo("-1500.00");
        assertThat(balances.get(MemberRef.registered(ANA))).isEqualByComparingTo("1500.00");
        sumIsZero(balances);
    }

    @Test
    void debtsInDifferentCurrenciesAreNeverAddedTogether() {
        Debt ars = registeredDebt(41L, JUAN, "1000.00");
        Debt usd = withCurrency(registeredDebt(42L, CARLA, "10.00"), Currency.USD);

        Map<Currency, Map<MemberRef, BigDecimal>> balances =
                BalanceCalculator.calculate(List.of(ars, usd));

        Map<MemberRef, BigDecimal> inArs = only(balances, Currency.ARS);
        Map<MemberRef, BigDecimal> inUsd = only(balances, Currency.USD);
        assertThat(inArs.get(MemberRef.registered(JUAN))).isEqualByComparingTo("-1000.00");
        assertThat(inArs.get(MemberRef.registered(CARLA))).isNull();
        assertThat(inUsd.get(MemberRef.registered(CARLA))).isEqualByComparingTo("-10.00");
        assertThat(inUsd.get(MemberRef.registered(JUAN))).isNull();
        assertThat(inUsd.get(MemberRef.registered(ANA))).isEqualByComparingTo("10.00");
        sumIsZero(inArs);
        sumIsZero(inUsd);
    }

    @Test
    void theResultIsOrderedAndImmutable() {
        Map<Currency, Map<MemberRef, BigDecimal>> result = BalanceCalculator.calculate(List.of(
                registeredDebt(41L, JUAN, "10.00"),
                simulatedDebt(42L, ABUELA, "5.00")));

        Map<MemberRef, BigDecimal> balances = only(result, Currency.ARS);
        assertThat(balances.keySet()).containsExactly(
                MemberRef.registered(ANA), MemberRef.registered(JUAN), MemberRef.simulated(ABUELA));
        assertThatThrownBy(() -> balances.put(MemberRef.registered(99L), BigDecimal.ZERO))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.put(Currency.USD, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> BalanceCalculator.calculate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BalanceCalculator.calculate(Arrays.asList((Debt) null)))
                .isInstanceOf(IllegalArgumentException.class);

        Debt withoutStatus = registeredDebt(41L, JUAN, "10.00");
        ReflectionTestUtils.setField(withoutStatus, "status", null);
        assertThatThrownBy(() -> BalanceCalculator.calculate(List.of(withoutStatus)))
                .isInstanceOf(IllegalArgumentException.class);

        Debt withoutCurrency = registeredDebt(42L, JUAN, "10.00");
        ReflectionTestUtils.setField(withoutCurrency, "currency", null);
        assertThatThrownBy(() -> BalanceCalculator.calculate(List.of(withoutCurrency)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memberRefKeepsTypeAndIdApart() {
        assertThat(MemberRef.registered(7L)).isNotEqualTo(MemberRef.simulated(7L));
        assertThat(MemberRef.registered(7L).isSimulated()).isFalse();
        assertThat(MemberRef.simulated(7L).isSimulated()).isTrue();
        assertThat(MemberRef.registered(7L)).isLessThan(MemberRef.simulated(1L));
        assertThatThrownBy(() -> new MemberRef(null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MemberRef.registered(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MemberRef.registered(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Property (T013): with a fixed-seed generator, every generated group of
     * debts produces one zero-sum map per currency, no currency is invented and
     * the calculation is deterministic.
     */
    @Test
    void everyGeneratedGroupSumsToZeroPerCurrency() {
        Random random = new Random(SEED);
        Currency[] currencies = Currency.values();
        DebtStatus[] statuses = DebtStatus.values();
        long shareId = 100L;

        for (int round = 0; round < 300; round++) {
            int size = 1 + random.nextInt(25);
            List<Debt> debts = new ArrayList<>(size);
            List<Debt> unsettled = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                long debtor = random.nextBoolean() ? JUAN : CARLA;
                String amount = BigDecimal.valueOf(random.nextInt(1, 100_000), 2).toPlainString();
                Debt debt = registeredDebt(shareId++, debtor, amount);
                withCurrency(debt, currencies[random.nextInt(currencies.length)]);
                DebtStatus status = statuses[random.nextInt(statuses.length)];
                withStatus(debt, status);
                debts.add(debt);
                if (!status.isSettled()) {
                    unsettled.add(debt);
                }
            }

            Map<Currency, Map<MemberRef, BigDecimal>> result = BalanceCalculator.calculate(debts);
            assertThat(result).isEqualTo(BalanceCalculator.calculate(debts));
            assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(
                    unsettled.stream().map(Debt::getCurrency).distinct().toList());
            result.values().forEach(BalanceCalculatorTest::sumIsZero);
        }
    }

    /** Property (T016): a simulated debtor never leaks into the registered id space. */
    @Test
    void everyGeneratedSimulatedDebtorKeepsItsOwnBalance() {
        Random random = new Random(SEED + 1);
        for (int round = 0; round < 100; round++) {
            long amount = random.nextInt(1, 100_000);
            Debt debt = simulatedDebt(500L + round, ABUELA + (round % 2), BigDecimal.valueOf(amount, 2).toPlainString());

            Map<MemberRef, BigDecimal> balances =
                    only(BalanceCalculator.calculate(List.of(debt)), Currency.ARS);

            assertThat(balances).containsKey(MemberRef.simulated(ABUELA + (round % 2)));
            assertThat(balances).doesNotContainKey(MemberRef.registered(ABUELA + (round % 2)));
            assertThat(balances.get(MemberRef.registered(ANA)))
                    .isEqualByComparingTo(BigDecimal.valueOf(amount, 2));
            sumIsZero(balances);
        }
    }
}
