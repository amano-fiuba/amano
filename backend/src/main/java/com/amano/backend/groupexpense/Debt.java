package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.amano.backend.user.Currency;
import com.amano.backend.user.CurrencyConversion;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Debt of one participant towards the payer of a group expense (table
 * {@code debts}, 017 data-model.md). Created {@code PENDING} for every share
 * that is not the payer's (FR-010). The status is stored and always matches
 * {@code paidAmount} (research.md D5); {@link #applyPayment} is the only way to
 * move it, prepared for the payments of 018.
 */
@Entity
@Table(name = "debts")
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Null for a direct debt between friends (027 US4). */
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "share_id", nullable = false, unique = true)
    private Long shareId;

    @Enumerated(EnumType.STRING)
    @Column(name = "debtor_type", nullable = false, length = 10)
    private ParticipantType debtorType;

    @Column(name = "debtor_user_id")
    private Long debtorUserId;

    @Column(name = "debtor_simulated_profile_id")
    private Long debtorSimulatedProfileId;

    @Column(name = "debtor_name", nullable = false)
    private String debtorName;

    @Column(name = "creditor_user_id", nullable = false)
    private Long creditorUserId;

    @Convert(converter = CurrencyConversion.class)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DebtStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Debt() {
    }

    /**
     * New {@code PENDING} debt of the participant of {@code share} towards the
     * payer of {@code expense}, for the whole share. The share must be saved
     * (it needs its id) and must not belong to the payer.
     */
    public static Debt pendingFor(GroupExpense expense, GroupExpenseShare share) {
        return pendingFor(expense, share, expense.getCurrency());
    }

    /**
     * New {@code PENDING} debt of the participant of {@code share} towards the
     * payer of {@code expense}, expressed in {@code debtCurrency} (022 D8: the
     * group's primary currency once the expense total is converted).
     */
    public static Debt pendingFor(GroupExpense expense, GroupExpenseShare share, Currency debtCurrency) {
        if (share.isOf(expense.getPayerUserId())) {
            throw new IllegalArgumentException("La parte del pagador no genera deuda");
        }
        Debt debt = new Debt();
        debt.groupId = expense.getGroupId();
        debt.expenseId = expense.getId();
        debt.shareId = share.getId();
        debt.debtorType = share.getParticipantType();
        debt.debtorUserId = share.getUserId();
        debt.debtorSimulatedProfileId = share.getSimulatedProfileId();
        debt.debtorName = share.getParticipantName();
        debt.creditorUserId = expense.getPayerUserId();
        debt.currency = debtCurrency != null ? debtCurrency : expense.getCurrency();
        debt.amount = share.getAmount();
        debt.paidAmount = BigDecimal.ZERO.setScale(2);
        debt.status = DebtStatus.PENDING;
        return debt;
    }

    /**
     * Applies a confirmed payment, partial or total (018 flow and 022 groups
     * that do not require validation): {@code 0 < payment <= outstanding}, at
     * most two decimals. The amount is added to {@code paidAmount} at once and
     * the status follows it: {@code PARTIALLY_PAID} while a balance remains,
     * {@code PAID} when it reaches zero (an {@code OVERDUE} debt that receives a
     * partial payment also becomes {@code PARTIALLY_PAID}, 022 D3). A debt that
     * is not payable ({@code PAYMENT_REPORTED}, {@code PAID}, {@code VERIFIED})
     * or an invalid amount is rejected without changes (FR-012).
     */
    public void applyPayment(BigDecimal payment) {
        if (!status.isPayable()) {
            throw new IllegalStateException("La deuda ya está pagada");
        }
        requireValidAmount(payment);
        BigDecimal newPaid = paidAmount.add(payment).setScale(2);
        status = DebtStatus.of(amount, newPaid);
        paidAmount = newPaid;
    }

    /**
     * Reports a payment that waits for an administrator (022 D6, groups that
     * require validation): the debt becomes {@code PAYMENT_REPORTED} and
     * {@code paidAmount} is NOT touched, so the debt is still not settled and
     * keeps contributing {@link #getOutstanding()} to the balances (D4) until the
     * payment is confirmed. The same validation as {@link #applyPayment} applies,
     * and a debt that already waits for a decision or is settled is rejected.
     */
    public void reportPayment(BigDecimal payment) {
        if (!status.isPayable()) {
            throw new IllegalStateException("La deuda ya tiene un pago registrado");
        }
        requireValidAmount(payment);
        status = DebtStatus.PAYMENT_REPORTED;
    }

    /**
     * 022 US5: an administrator confirmed the reported payment. The amount of
     * that payment is ADDED to {@code paidAmount} (never set to the debt amount)
     * and the status follows the balance: {@code VERIFIED} when nothing is left
     * (equivalent to {@code PAID} for balances), {@code PARTIALLY_PAID} otherwise.
     */
    public void confirmReportedPayment(BigDecimal payment) {
        if (status != DebtStatus.PAYMENT_REPORTED) {
            throw new IllegalStateException("La deuda no tiene un pago pendiente de validación");
        }
        requireValidAmount(payment);
        BigDecimal newPaid = paidAmount.add(payment).setScale(2);
        paidAmount = newPaid;
        status = newPaid.compareTo(amount) == 0 ? DebtStatus.VERIFIED : DebtStatus.PARTIALLY_PAID;
    }

    /**
     * 022 US5: an administrator rejected the reported payment. Reporting never
     * changed {@code paidAmount}, so the status simply follows it again:
     * {@code PENDING} when nothing was paid, {@code PARTIALLY_PAID} when earlier
     * payments exist. A debt that had been overdue returns as {@code PENDING}
     * and the overdue job (D7) marks it again on its next run.
     */
    public void rejectReportedPayment() {
        if (status != DebtStatus.PAYMENT_REPORTED) {
            throw new IllegalStateException("La deuda no tiene un pago pendiente de validación");
        }
        status = DebtStatus.of(amount, paidAmount);
    }

    private void requireValidAmount(BigDecimal payment) {
        if (payment == null || payment.signum() <= 0 || payment.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto del pago no es válido");
        }
        if (payment.compareTo(getOutstanding()) > 0) {
            throw new IllegalArgumentException("El pago supera el saldo de la deuda");
        }
    }

    /** Amount still to pay: {@code amount - paidAmount}. */
    public BigDecimal getOutstanding() {
        return amount.subtract(paidAmount);
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public Long getShareId() {
        return shareId;
    }

    public ParticipantType getDebtorType() {
        return debtorType;
    }

    public Long getDebtorUserId() {
        return debtorUserId;
    }

    public Long getDebtorSimulatedProfileId() {
        return debtorSimulatedProfileId;
    }

    public String getDebtorName() {
        return debtorName;
    }

    public Long getCreditorUserId() {
        return creditorUserId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public DebtStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
