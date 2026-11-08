package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.groupexpense.balance.BalanceCalculator;
import com.amano.backend.groupexpense.balance.MemberRef;
import com.amano.backend.user.Currency;

/**
 * 022 T081 (plan.md D3/D4/D6, partial payments): property tests with a fixed seed and no extra
 * dependency. Random sequences of payments (valid and invalid), reports, confirmations, rejections
 * and overdue marks are applied to debts, next to a tiny model that only knows the rules of the
 * design: the confirmed payments add up to {@code paid_amount}, a reported payment does not touch
 * it, an invalid operation changes nothing. After EVERY step the invariants must hold:
 * <ul>
 * <li>the sum of the confirmed payments never exceeds the debt amount and equals {@code paid_amount};</li>
 * <li>the status agrees with the balance (PENDING/OVERDUE with nothing paid, PARTIALLY_PAID with a
 * balance left, PAID/VERIFIED at zero, PAYMENT_REPORTED while a payment waits);</li>
 * <li>an invalid operation leaves the debt untouched;</li>
 * <li>the balances of the group add up to zero in every currency and use the outstanding balance of
 * each unsettled debt (a reported payment keeps counting in full).</li>
 * </ul>
 */
class PartialPaymentPropertyTest {

    private static final long ANA = 7L;
    private static final long[] DEBTORS = { 15L, 16L, 17L };
    private static final long ABUELA = 4L;

    /** Fixed seed: deterministic and reproducible (printed in the assertion descriptions). */
    private static final long SEED = 20261107L;
    private static final int ROUNDS = 300;
    private static final int STEPS = 40;

    /** The debt plus what the model of the design says about it. */
    private static final class Ledger {

        final Debt debt;
        BigDecimal confirmed = BigDecimal.ZERO.setScale(2);
        /** Amount of the payment waiting for validation, or null. */
        BigDecimal reported;

        Ledger(Debt debt) {
            this.debt = debt;
        }

        BigDecimal outstanding() {
            return debt.getAmount().subtract(confirmed);
        }
    }

    private enum Operation { PAY, REPORT, CONFIRM, REJECT, MARK_OVERDUE }

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

    private static Debt debt(long id, Random random) {
        BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 200_000), 2);
        GroupExpenseShare share = random.nextInt(5) == 0
                ? GroupExpenseShare.simulated(12L, 2, ABUELA, "Abuela", amount)
                : GroupExpenseShare.registered(12L, 1, DEBTORS[random.nextInt(DEBTORS.length)], "Deudor", amount);
        ReflectionTestUtils.setField(share, "id", id);
        Debt debt = Debt.pendingFor(expense(), share);
        ReflectionTestUtils.setField(debt, "id", id);
        ReflectionTestUtils.setField(debt, "currency", Currency.values()[random.nextInt(Currency.values().length)]);
        return debt;
    }

    /** A payment amount that is often valid and often not: from -0.05 up to the balance + 3.00. */
    private static BigDecimal amountFor(Ledger ledger, Random random) {
        long outstandingCents = ledger.debt.getOutstanding().movePointRight(2).longValueExact();
        long cents = switch (random.nextInt(5)) {
            case 0 -> outstandingCents;              // exactly the balance
            case 1 -> 1;                             // the smallest payment
            case 2 -> outstandingCents + 1 + random.nextInt(300); // above the balance
            case 3 -> -random.nextInt(0, 6);         // zero or negative
            default -> 1 + random.nextInt((int) Math.max(1, Math.min(outstandingCents, 100_000)));
        };
        return BigDecimal.valueOf(cents, 2);
    }

    /** Applies one random operation to the ledger following the model and checks the "no change when rejected" rule. */
    private static void step(Ledger ledger, Operation operation, Random random, String where) {
        Debt debt = ledger.debt;
        DebtStatus statusBefore = debt.getStatus();
        BigDecimal paidBefore = debt.getPaidAmount();
        boolean payable = statusBefore.isPayable();
        BigDecimal amount = amountFor(ledger, random);
        boolean validAmount = amount.signum() > 0 && amount.compareTo(debt.getOutstanding()) <= 0;
        boolean rejected = false;

        switch (operation) {
            case PAY -> {
                if (payable && validAmount) {
                    debt.applyPayment(amount);
                    ledger.confirmed = ledger.confirmed.add(amount);
                }
                else {
                    rejected = true;
                    expectRejected(() -> debt.applyPayment(amount), where);
                }
            }
            case REPORT -> {
                if (payable && validAmount) {
                    debt.reportPayment(amount);
                    ledger.reported = amount;
                }
                else {
                    rejected = true;
                    expectRejected(() -> debt.reportPayment(amount), where);
                }
            }
            case CONFIRM -> {
                if (ledger.reported != null) {
                    BigDecimal toConfirm = ledger.reported;
                    debt.confirmReportedPayment(toConfirm);
                    ledger.confirmed = ledger.confirmed.add(toConfirm);
                    ledger.reported = null;
                }
                else {
                    rejected = true;
                    expectRejected(() -> debt.confirmReportedPayment(amount), where);
                }
            }
            case REJECT -> {
                if (ledger.reported != null) {
                    debt.rejectReportedPayment();
                    ledger.reported = null;
                }
                else {
                    rejected = true;
                    expectRejected(debt::rejectReportedPayment, where);
                }
            }
            case MARK_OVERDUE -> {
                // What the daily job does: only a PENDING debt becomes OVERDUE.
                if (statusBefore == DebtStatus.PENDING) {
                    ReflectionTestUtils.setField(debt, "status", DebtStatus.OVERDUE);
                }
            }
        }
        if (rejected) {
            assertThat(debt.getStatus()).as("rejected operation changed the status, %s", where).isEqualTo(statusBefore);
            assertThat(debt.getPaidAmount()).as("rejected operation changed the paid amount, %s", where)
                    .isEqualByComparingTo(paidBefore);
        }
    }

    private static void expectRejected(Runnable call, String where) {
        try {
            call.run();
        }
        catch (IllegalStateException | IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("The operation should have been rejected, " + where);
    }

    /** Drives {@code ROUNDS} random single-debt histories and hands every step to the checker. */
    private static void simulateDebts(long seed, BiConsumer<Ledger, String> checker) {
        Random random = new Random(seed);
        Operation[] operations = Operation.values();
        for (int round = 0; round < ROUNDS; round++) {
            Ledger ledger = new Ledger(debt(100L + round, random));
            for (int i = 0; i < STEPS; i++) {
                Operation operation = operations[random.nextInt(operations.length)];
                String where = "seed=" + seed + " round=" + round + " step=" + i + " op=" + operation;
                step(ledger, operation, random, where);
                checker.accept(ledger, where);
            }
        }
    }

    @Test
    void theSumOfTheConfirmedPaymentsNeverExceedsTheDebtAndEqualsPaidAmount() {
        simulateDebts(SEED, (ledger, where) -> {
            Debt debt = ledger.debt;
            assertThat(ledger.confirmed).as(where).isLessThanOrEqualTo(debt.getAmount());
            assertThat(debt.getPaidAmount()).as("paid_amount vs confirmed payments, %s", where)
                    .isEqualByComparingTo(ledger.confirmed);
            assertThat(debt.getOutstanding()).as(where).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(debt.getOutstanding()).as(where).isEqualByComparingTo(ledger.outstanding());
            // A payment waiting for validation never counts as paid, and it always fits in the balance.
            if (ledger.reported != null) {
                assertThat(ledger.reported).as(where).isLessThanOrEqualTo(debt.getOutstanding());
            }
        });
    }

    @Test
    void theStatusIsAlwaysCoherentWithThePaidAmount() {
        simulateDebts(SEED + 1, (ledger, where) -> {
            Debt debt = ledger.debt;
            DebtStatus status = debt.getStatus();
            if (ledger.reported != null) {
                assertThat(status).as(where).isEqualTo(DebtStatus.PAYMENT_REPORTED);
                assertThat(status.isSettled()).as(where).isFalse();
                assertThat(status.isPayable()).as("only one payment waits at a time, %s", where).isFalse();
            }
            else if (ledger.confirmed.signum() == 0) {
                assertThat(status).as(where).isIn(DebtStatus.PENDING, DebtStatus.OVERDUE);
            }
            else if (ledger.confirmed.compareTo(debt.getAmount()) < 0) {
                assertThat(status).as(where).isEqualTo(DebtStatus.PARTIALLY_PAID);
                assertThat(status.isPayable()).as(where).isTrue();
            }
            else {
                assertThat(status).as(where).isIn(DebtStatus.PAID, DebtStatus.VERIFIED);
                assertThat(status.isSettled()).as(where).isTrue();
                assertThat(debt.getOutstanding()).as(where).isEqualByComparingTo(BigDecimal.ZERO);
            }
        });
    }

    /**
     * Operations that the design forbids are ALWAYS rejected and change nothing: a payment above the
     * balance, zero, negative or null; any payment on a settled debt or on one that already waits for a
     * decision; confirming or rejecting when nothing is reported.
     */
    @Test
    void forbiddenOperationsAreAlwaysRejectedAndNeverChangeTheDebt() {
        Random random = new Random(SEED + 2);
        Operation[] operations = Operation.values();
        for (int round = 0; round < ROUNDS; round++) {
            Ledger ledger = new Ledger(debt(500L + round, random));
            for (int i = 0; i < STEPS; i++) {
                step(ledger, operations[random.nextInt(operations.length)], random, "warm-up round=" + round);
                Debt debt = ledger.debt;
                String where = "seed=" + (SEED + 2) + " round=" + round + " step=" + i;
                DebtStatus status = debt.getStatus();
                BigDecimal paid = debt.getPaidAmount();

                BigDecimal above = debt.getOutstanding().add(new BigDecimal("0.01"));
                for (BigDecimal bad : new BigDecimal[] { above, BigDecimal.ZERO, new BigDecimal("-0.01"), null }) {
                    expectRejected(() -> debt.applyPayment(bad), where + " pay " + bad);
                    expectRejected(() -> debt.reportPayment(bad), where + " report " + bad);
                }
                if (!status.isPayable()) {
                    BigDecimal one = new BigDecimal("0.01");
                    expectRejected(() -> debt.applyPayment(one), where + " pay on " + status);
                    expectRejected(() -> debt.reportPayment(one), where + " report on " + status);
                }
                if (status != DebtStatus.PAYMENT_REPORTED) {
                    expectRejected(debt::rejectReportedPayment, where + " reject without a reported payment");
                    expectRejected(() -> debt.confirmReportedPayment(new BigDecimal("0.01")),
                            where + " confirm without a reported payment");
                }
                else {
                    expectRejected(() -> debt.confirmReportedPayment(above), where + " confirm above the balance");
                }
                assertThat(debt.getStatus()).as(where).isEqualTo(status);
                assertThat(debt.getPaidAmount()).as(where).isEqualByComparingTo(paid);
            }
        }
    }

    @Test
    void theBalancesOfTheGroupAlwaysSumToZeroAndUseTheOutstandingBalance() {
        Random random = new Random(SEED + 3);
        Operation[] operations = Operation.values();
        for (int round = 0; round < ROUNDS; round++) {
            List<Ledger> ledgers = new ArrayList<>();
            int size = 2 + random.nextInt(7);
            for (int i = 0; i < size; i++) {
                ledgers.add(new Ledger(debt(1000L + round * 10L + i, random)));
            }
            for (int i = 0; i < STEPS; i++) {
                Ledger ledger = ledgers.get(random.nextInt(ledgers.size()));
                Operation operation = operations[random.nextInt(operations.length)];
                String where = "seed=" + (SEED + 3) + " round=" + round + " step=" + i + " op=" + operation;
                step(ledger, operation, random, where);

                List<Debt> debts = ledgers.stream().map(l -> l.debt).toList();
                Map<Currency, Map<MemberRef, BigDecimal>> balances = BalanceCalculator.calculate(debts);
                for (Currency currency : Currency.values()) {
                    Map<MemberRef, BigDecimal> perMember = balances.getOrDefault(currency, Map.of());
                    BigDecimal sum = perMember.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    assertThat(sum).as("balances of %s must add up to zero, %s", currency, where)
                            .isEqualByComparingTo(BigDecimal.ZERO);

                    // What the creditor is owed is exactly the sum of the outstanding balances of the
                    // unsettled debts of that currency (a reported payment still counts in full).
                    BigDecimal outstanding = ledgers.stream()
                            .filter(l -> l.debt.getCurrency() == currency && !l.debt.getStatus().isSettled())
                            .map(l -> l.outstanding())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal owedToAna = perMember.getOrDefault(MemberRef.registered(ANA), BigDecimal.ZERO);
                    assertThat(owedToAna).as("credit of the payer in %s, %s", currency, where)
                            .isEqualByComparingTo(outstanding);
                }
            }
        }
    }
}
