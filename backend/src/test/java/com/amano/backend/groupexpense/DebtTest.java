package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.user.Currency;

/**
 * 017 T008: debt states and transitions (research.md D5, FR-011/FR-012). The
 * status always follows {@code paidAmount}; payments only move forward and never
 * exceed the outstanding amount. 018 calls {@link Debt#applyPayment}.
 */
class DebtTest {

    private static GroupExpense expense() {
        GroupExpense expense = new GroupExpense();
        ReflectionTestUtils.setField(expense, "id", 12L);
        expense.setGroupId(3L);
        expense.setCreatedByUserId(7L);
        expense.setPayerUserId(7L);
        expense.setAmount(new BigDecimal("150.00"));
        expense.setCurrency(Currency.USD);
        expense.setExpenseDate(LocalDate.of(2026, 11, 5));
        expense.setIdempotencyKey(UUID.randomUUID());
        return expense;
    }

    private static GroupExpenseShare registeredShare(Long userId, String amount) {
        GroupExpenseShare share = GroupExpenseShare.registered(12L, 1, userId, "Juan Pérez",
                new BigDecimal(amount));
        ReflectionTestUtils.setField(share, "id", 40L);
        return share;
    }

    private static Debt pending(String amount) {
        return Debt.pendingFor(expense(), registeredShare(15L, amount));
    }

    @Test
    void statusIsDerivedFromThePaidAmount() {
        BigDecimal amount = new BigDecimal("50.00");

        assertThat(DebtStatus.of(amount, BigDecimal.ZERO)).isEqualTo(DebtStatus.PENDING);
        assertThat(DebtStatus.of(amount, new BigDecimal("0.01"))).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(DebtStatus.of(amount, new BigDecimal("49.99"))).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(DebtStatus.of(amount, new BigDecimal("50.00"))).isEqualTo(DebtStatus.PAID);
        assertThat(DebtStatus.of(amount, new BigDecimal("50"))).isEqualTo(DebtStatus.PAID);
    }

    @Test
    void statusRejectsImpossiblePaidAmounts() {
        BigDecimal amount = new BigDecimal("50.00");

        assertThatThrownBy(() -> DebtStatus.of(amount, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DebtStatus.of(amount, new BigDecimal("50.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DebtStatus.of(amount, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyPaidAndVerifiedAreSettled() {
        assertThat(DebtStatus.PENDING.isSettled()).isFalse();
        assertThat(DebtStatus.PARTIALLY_PAID.isSettled()).isFalse();
        assertThat(DebtStatus.PAYMENT_REPORTED.isSettled()).isFalse();
        assertThat(DebtStatus.OVERDUE.isSettled()).isFalse();
        assertThat(DebtStatus.PAID.isSettled()).isTrue();
        assertThat(DebtStatus.VERIFIED.isSettled()).isTrue();

        assertThat(DebtStatus.PENDING.isPayable()).isTrue();
        assertThat(DebtStatus.OVERDUE.isPayable()).isTrue();
        assertThat(DebtStatus.PAYMENT_REPORTED.isPayable()).isFalse();
        assertThat(DebtStatus.PAID.isPayable()).isFalse();
        assertThat(DebtStatus.VERIFIED.isPayable()).isFalse();
    }

    /**
     * 022 T080 (rewritten: DebtStatus.settledBy only knew full payments and was removed): the
     * group setting decides between immediate effect (applyPayment) and reporting (reportPayment).
     */
    @Test
    void thePaymentEntryPointDependsOnTheGroupSetting() {
        Debt immediate = pending("50.00");
        immediate.applyPayment(new BigDecimal("50.00"));
        assertThat(immediate.getStatus()).isEqualTo(DebtStatus.PAID);

        Debt reported = pending("50.00");
        reported.reportPayment(new BigDecimal("50.00"));
        assertThat(reported.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
    }

    @Test
    void aNewDebtCopiesTheShareAndStartsPending() {
        Debt debt = pending("50.00");

        assertThat(debt.getGroupId()).isEqualTo(3L);
        assertThat(debt.getExpenseId()).isEqualTo(12L);
        assertThat(debt.getShareId()).isEqualTo(40L);
        assertThat(debt.getDebtorType()).isEqualTo(ParticipantType.REGISTERED);
        assertThat(debt.getDebtorUserId()).isEqualTo(15L);
        assertThat(debt.getDebtorSimulatedProfileId()).isNull();
        assertThat(debt.getDebtorName()).isEqualTo("Juan Pérez");
        assertThat(debt.getCreditorUserId()).isEqualTo(7L);
        assertThat(debt.getCurrency()).isEqualTo(Currency.USD);
        assertThat(debt.getAmount()).isEqualByComparingTo("50.00");
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("50.00");
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PENDING);
    }

    @Test
    void aSimulatedShareProducesASimulatedDebtor() {
        GroupExpenseShare share = GroupExpenseShare.simulated(12L, 2, 4L, "Abuela", new BigDecimal("25.00"));
        ReflectionTestUtils.setField(share, "id", 41L);

        Debt debt = Debt.pendingFor(expense(), share);

        assertThat(debt.getDebtorType()).isEqualTo(ParticipantType.SIMULATED);
        assertThat(debt.getDebtorUserId()).isNull();
        assertThat(debt.getDebtorSimulatedProfileId()).isEqualTo(4L);
        assertThat(debt.getDebtorName()).isEqualTo("Abuela");
    }

    @Test
    void thePayerShareNeverBecomesADebt() {
        assertThatThrownBy(() -> Debt.pendingFor(expense(), registeredShare(7L, "50.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPartialPaymentMovesPendingToPartiallyPaidAndThenToPaid() {
        Debt debt = pending("50.00");

        debt.applyPayment(new BigDecimal("20.00"));
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("20.00");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("30.00");

        debt.applyPayment(new BigDecimal("30.00"));
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
        assertThat(debt.getOutstanding()).isEqualByComparingTo("0");
    }

    @Test
    void aFullPaymentMovesPendingDirectlyToPaid() {
        Debt debt = pending("50.00");

        debt.applyPayment(new BigDecimal("50.00"));

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void invalidPaymentAmountsAreRejectedWithoutChanges() {
        Debt debt = pending("50.00");
        debt.applyPayment(new BigDecimal("10.00"));

        assertThatThrownBy(() -> debt.applyPayment(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> debt.applyPayment(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> debt.applyPayment(new BigDecimal("-5.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> debt.applyPayment(new BigDecimal("40.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> debt.applyPayment(new BigDecimal("0.001")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(debt.getPaidAmount()).isEqualByComparingTo("10.00");
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
    }

    @Test
    void aPaidDebtAcceptsNoMorePayments() {
        Debt debt = pending("50.00");
        debt.applyPayment(new BigDecimal("50.00"));

        assertThatThrownBy(() -> debt.applyPayment(new BigDecimal("1.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("50.00");
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
    }

    @Test
    void aReportedDebtRejectsPayments() {
        Debt debt = pending("50.00");
        ReflectionTestUtils.setField(debt, "status", DebtStatus.PAYMENT_REPORTED);

        assertThatThrownBy(() -> debt.applyPayment(new BigDecimal("1.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La deuda ya está pagada");
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
    }

    /** Rewritten for partial payments: a payment for the whole balance still ends in PAID / PAYMENT_REPORTED. */
    @Test
    void aPaymentOfTheWholeBalanceMovesTheDebtToPaidOrPaymentReported() {
        Debt settled = pending("50.00");

        settled.applyPayment(new BigDecimal("50.00"));

        assertThat(settled.getStatus()).isEqualTo(DebtStatus.PAID);
        assertThat(settled.getPaidAmount()).isEqualByComparingTo("50.00");
        assertThat(settled.getOutstanding()).isEqualByComparingTo("0.00");

        Debt reported = pending("50.00");

        reported.reportPayment(new BigDecimal("50.00"));

        // Reported is NOT settled (D4): nothing is paid yet and the whole amount keeps counting.
        assertThat(reported.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
        assertThat(reported.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(reported.getOutstanding()).isEqualByComparingTo("50.00");
        assertThat(reported.getStatus().isSettled()).isFalse();
    }

    @Test
    void aPartialPaymentAddsToPaidAmountAndLeavesTheDebtPartiallyPaidUntilItReachesZero() {
        Debt debt = pending("50.00");

        debt.applyPayment(new BigDecimal("20.00"));

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("20.00");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("30.00");

        debt.applyPayment(new BigDecimal("10.00"));
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(debt.getOutstanding()).isEqualByComparingTo("20.00");

        debt.applyPayment(new BigDecimal("20.00"));
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void aPaymentAboveTheBalanceOrNotPositiveIsRejectedWithoutChanges() {
        Debt debt = pending("50.00");
        debt.applyPayment(new BigDecimal("20.00"));

        for (String bad : new String[] { "30.01", "0", "-1", "10.001" }) {
            assertThatThrownBy(() -> debt.applyPayment(new BigDecimal(bad)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> debt.reportPayment(new BigDecimal(bad)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> debt.applyPayment(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("20.00");
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
    }

    /** Rewritten: a payment on a legacy partial or overdue debt works for any amount up to the balance. */
    @Test
    void aPaymentOnALegacyPartialOrOverdueDebtWorks() {
        Debt partial = pending("50.00");
        partial.applyPayment(new BigDecimal("20.00"));

        partial.reportPayment(new BigDecimal("30.00"));

        assertThat(partial.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
        assertThat(partial.getPaidAmount()).isEqualByComparingTo("20.00");
        assertThat(partial.getOutstanding()).isEqualByComparingTo("30.00");

        Debt overdueTotal = pending("50.00");
        ReflectionTestUtils.setField(overdueTotal, "status", DebtStatus.OVERDUE);

        overdueTotal.applyPayment(new BigDecimal("50.00"));

        assertThat(overdueTotal.getStatus()).isEqualTo(DebtStatus.PAID);
    }

    /** 022 D3: a partial payment moves an OVERDUE debt to PARTIALLY_PAID (decision to confirm). */
    @Test
    void aPartialPaymentOnAnOverdueDebtLeavesItPartiallyPaid() {
        Debt overdue = pending("50.00");
        ReflectionTestUtils.setField(overdue, "status", DebtStatus.OVERDUE);

        overdue.applyPayment(new BigDecimal("10.00"));

        assertThat(overdue.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(overdue.getOutstanding()).isEqualByComparingTo("40.00");
    }

    /** Rewritten: a debt that waits for a decision or is settled rejects another payment. */
    @Test
    void aReportedOrSettledDebtRejectsAnotherPayment() {
        Debt reported = pending("50.00");
        reported.reportPayment(new BigDecimal("10.00"));

        assertThatThrownBy(() -> reported.applyPayment(new BigDecimal("5.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reported.reportPayment(new BigDecimal("5.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La deuda ya tiene un pago registrado");
        assertThat(reported.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
        assertThat(reported.getPaidAmount()).isEqualByComparingTo("0.00");

        Debt paid = pending("50.00");
        paid.applyPayment(new BigDecimal("50.00"));

        assertThatThrownBy(() -> paid.applyPayment(new BigDecimal("1.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> paid.reportPayment(new BigDecimal("1.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(paid.getStatus()).isEqualTo(DebtStatus.PAID);
    }

    @Test
    void anOverdueDebtAcceptsPayments() {
        Debt debt = pending("50.00");
        ReflectionTestUtils.setField(debt, "status", DebtStatus.OVERDUE);

        debt.applyPayment(new BigDecimal("50.00"));

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("50.00");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("0.00");
    }

    /** Rewritten: confirming a payment that covers the whole balance verifies the debt (the amount is ADDED). */
    @Test
    void aReportedPaymentOfTheWholeBalanceMovesTheDebtToVerifiedOnConfirmation() {
        Debt debt = pending("50.00");
        debt.reportPayment(new BigDecimal("50.00"));
        assertThat(debt.getOutstanding()).isEqualByComparingTo("50.00");

        debt.confirmReportedPayment(new BigDecimal("50.00"));

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.VERIFIED);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("50.00");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("0.00");
        assertThat(debt.getStatus().isSettled()).isTrue();
    }

    @Test
    void confirmingAPartialReportedPaymentAddsItAndLeavesTheDebtPartiallyPaid() {
        Debt debt = pending("50.00");
        debt.reportPayment(new BigDecimal("20.00"));

        debt.confirmReportedPayment(new BigDecimal("20.00"));

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("20.00");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("30.00");
        assertThat(debt.getStatus().isPayable()).isTrue();

        // The rest can be reported and confirmed again, ending VERIFIED.
        debt.reportPayment(new BigDecimal("30.00"));
        debt.confirmReportedPayment(new BigDecimal("30.00"));
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.VERIFIED);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("50.00");
    }

    /** Rewritten: rejecting returns the debt to the state its paid amount dictates (nothing paid: PENDING). */
    @Test
    void aRejectedReportedPaymentReturnsTheDebtToPending() {
        Debt debt = pending("50.00");
        debt.reportPayment(new BigDecimal("20.00"));

        debt.rejectReportedPayment();

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PENDING);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("50.00");
        assertThat(debt.getStatus().isPayable()).isTrue();
    }

    @Test
    void rejectingAReportedPaymentOfAPartiallyPaidDebtReturnsItToPartiallyPaid() {
        Debt debt = pending("50.00");
        debt.applyPayment(new BigDecimal("20.00"));
        debt.reportPayment(new BigDecimal("30.00"));

        debt.rejectReportedPayment();

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("20.00");
        assertThat(debt.getOutstanding()).isEqualByComparingTo("30.00");
    }

    @Test
    void onlyReportedDebtsAcceptAConfirmationOrRejection() {
        Debt pendingDebt = pending("50.00");

        assertThatThrownBy(() -> pendingDebt.confirmReportedPayment(new BigDecimal("10.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(pendingDebt::rejectReportedPayment)
                .isInstanceOf(IllegalStateException.class);
        assertThat(pendingDebt.getStatus()).isEqualTo(DebtStatus.PENDING);

        Debt settled = pending("50.00");
        settled.applyPayment(new BigDecimal("50.00"));

        assertThatThrownBy(settled::rejectReportedPayment).isInstanceOf(IllegalStateException.class);
        assertThat(settled.getStatus()).isEqualTo(DebtStatus.PAID);

        Debt reported = pending("50.00");
        reported.reportPayment(new BigDecimal("10.00"));

        assertThatThrownBy(() -> reported.confirmReportedPayment(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reported.confirmReportedPayment(new BigDecimal("50.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reported.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
        assertThat(reported.getPaidAmount()).isEqualByComparingTo("0.00");
    }
}
