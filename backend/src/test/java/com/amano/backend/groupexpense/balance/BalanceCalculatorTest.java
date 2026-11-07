package com.amano.backend.groupexpense.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseShare;
import com.amano.backend.user.Currency;

/**
 * 022 T009 (D4): the balance is derived from the outstanding debts, includes
 * simulated members and always sums to zero.
 */
class BalanceCalculatorTest {

    private static final long ANA = 7L;
    private static final long JUAN = 15L;
    private static final long ABUELA = 4L;

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
        Map<MemberRef, BigDecimal> balances =
                BalanceCalculator.calculate(List.of(registeredDebt(41L, JUAN, "3333.33")));

        assertThat(balances.get(MemberRef.registered(JUAN))).isEqualByComparingTo("-3333.33");
        assertThat(balances.get(MemberRef.registered(ANA))).isEqualByComparingTo("3333.33");
        sumIsZero(balances);
    }

    @Test
    void aSimulatedDebtorIsADifferentMemberFromARegisteredUserWithTheSameId() {
        Map<MemberRef, BigDecimal> balances =
                BalanceCalculator.calculate(List.of(simulatedDebt(42L, ABUELA, "1000.00")));

        assertThat(balances.get(MemberRef.simulated(ABUELA))).isEqualByComparingTo("-1000.00");
        assertThat(balances).doesNotContainKey(MemberRef.registered(ABUELA));
        sumIsZero(balances);
    }

    @Test
    void aPartialPaymentOnlyContributesTheRemainingBalance() {
        Debt debt = registeredDebt(41L, JUAN, "3333.33");
        debt.applyPayment(new BigDecimal("1000.00"));

        Map<MemberRef, BigDecimal> balances = BalanceCalculator.calculate(List.of(debt));

        assertThat(balances.get(MemberRef.registered(JUAN))).isEqualByComparingTo("-2333.33");
        assertThat(balances.get(MemberRef.registered(ANA))).isEqualByComparingTo("2333.33");
        sumIsZero(balances);
    }

    @Test
    void settledDebtsDoNotContribute() {
        Debt paid = registeredDebt(41L, JUAN, "1000.00");
        paid.applyPayment(new BigDecimal("1000.00"));

        Debt verified = registeredDebt(42L, JUAN, "500.00");
        ReflectionTestUtils.setField(verified, "status", DebtStatus.VERIFIED);
        ReflectionTestUtils.setField(verified, "paidAmount", new BigDecimal("500.00"));

        assertThat(BalanceCalculator.calculate(List.of(paid, verified))).isEmpty();
    }

    @Test
    void reportedAndOverdueDebtsStillCount() {
        Debt reported = registeredDebt(41L, JUAN, "1000.00");
        ReflectionTestUtils.setField(reported, "status", DebtStatus.PAYMENT_REPORTED);

        Debt overdue = registeredDebt(42L, JUAN, "500.00");
        ReflectionTestUtils.setField(overdue, "status", DebtStatus.OVERDUE);

        Map<MemberRef, BigDecimal> balances = BalanceCalculator.calculate(List.of(reported, overdue));

        assertThat(balances.get(MemberRef.registered(JUAN))).isEqualByComparingTo("-1500.00");
        assertThat(balances.get(MemberRef.registered(ANA))).isEqualByComparingTo("1500.00");
        sumIsZero(balances);
    }

    @Test
    void theResultIsOrderedAndImmutable() {
        Map<MemberRef, BigDecimal> balances = BalanceCalculator.calculate(List.of(
                registeredDebt(41L, JUAN, "10.00"),
                simulatedDebt(42L, ABUELA, "5.00")));

        assertThat(balances.keySet()).containsExactly(
                MemberRef.registered(ANA), MemberRef.registered(JUAN), MemberRef.simulated(ABUELA));
        assertThatThrownBy(() -> balances.put(MemberRef.registered(99L), BigDecimal.ZERO))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> BalanceCalculator.calculate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BalanceCalculator.calculate(java.util.Arrays.asList((Debt) null)))
                .isInstanceOf(IllegalArgumentException.class);

        Debt withoutStatus = registeredDebt(41L, JUAN, "10.00");
        ReflectionTestUtils.setField(withoutStatus, "status", null);
        assertThatThrownBy(() -> BalanceCalculator.calculate(List.of(withoutStatus)))
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

}
