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

    @Column(name = "group_id", nullable = false)
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
        debt.currency = expense.getCurrency();
        debt.amount = share.getAmount();
        debt.paidAmount = BigDecimal.ZERO.setScale(2);
        debt.status = DebtStatus.PENDING;
        return debt;
    }

    /**
     * Applies a payment (used by 018): {@code 0 < payment <= outstanding}, at most
     * two decimals. Moves PENDING → PARTIALLY_PAID → PAID or PENDING → PAID; a
     * debt that is not payable (PAYMENT_REPORTED/PAID/VERIFIED) or an invalid
     * amount is rejected without changes (FR-012). 022 keeps this legacy behavior
     * for the 018 flow and adds the full-payment transitions through
     * {@link DebtStatus#settledBy(boolean)}.
     */
    public void applyPayment(BigDecimal payment) {
        if (!status.isPayable()) {
            throw new IllegalStateException("La deuda ya está pagada");
        }
        if (payment == null || payment.signum() <= 0 || payment.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto del pago no es válido");
        }
        if (payment.compareTo(getOutstanding()) > 0) {
            throw new IllegalArgumentException("El pago supera el saldo de la deuda");
        }
        BigDecimal newPaid = paidAmount.add(payment).setScale(2);
        status = DebtStatus.of(amount, newPaid);
        paidAmount = newPaid;
    }

    /**
     * Applies a full payment of the remaining balance (022 D3/D6): moves
     * {@code PENDING}, {@code PARTIALLY_PAID} or {@code OVERDUE} to
     * {@code PAYMENT_REPORTED} when the group requires validation, or to
     * {@code PAID} when it does not. A debt that already received a reported or
     * settled payment is rejected without changes.
     */
    public void applyFullPayment(boolean requiresValidation) {
        if (!status.isPayable()) {
            throw new IllegalStateException("La deuda ya tiene un pago registrado");
        }
        if (getOutstanding().signum() <= 0) {
            throw new IllegalStateException("La deuda no tiene saldo pendiente");
        }
        paidAmount = amount.setScale(2);
        status = DebtStatus.settledBy(requiresValidation);
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
