package com.amano.backend.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.ParticipantType;
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
 * Payment of a group debt (table {@code payments}, 018 data-model.md). Payer,
 * receiver, group and currency are always copied from the debt (plan.md D3):
 * the client never chooses them. Names are copied at payment time
 * (preservation pattern of 010/014). Payments are immutable: no setters.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "debt_id", nullable = false)
    private Long debtId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 10)
    private ParticipantType payerType;

    @Column(name = "payer_user_id")
    private Long payerUserId;

    @Column(name = "payer_simulated_profile_id")
    private Long payerSimulatedProfileId;

    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Column(name = "receiver_user_id", nullable = false)
    private Long receiverUserId;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "registered_by_user_id", nullable = false)
    private Long registeredByUserId;

    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Convert(converter = CurrencyConversion.class)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    /** 022: payment state; the 018 flow writes CONFIRMED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /** 022: administrator that confirmed/rejected, when applicable. */
    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    /** 022: instant of the administrator decision, when applicable. */
    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    /**
     * 022: pre-022 payment (always TRUE for rows that existed before V18).
     * Legacy rows are excluded from the partial unique index on {@code debt_id}
     * so the partial payments of 018 keep coexisting.
     */
    @Column(name = "legacy", nullable = false)
    private boolean legacy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Payment() {
    }

    /**
     * New payment of {@code amount} on {@code debt}: the debtor pays the
     * creditor, in the debt currency and group (data-model.md regla 3).
     */
    public static Payment of(Debt debt, BigDecimal amount, LocalDate date, Long registeredByUserId,
            String receiverName, UUID idempotencyKey) {
        return of(debt, amount, date, registeredByUserId, receiverName, idempotencyKey, false);
    }

    /**
     * New payment with an explicit {@code legacy} flag (022 D6). The 018 flow
     * passes {@code true} while it still accepts partial payments, so those rows
     * stay outside the 022 partial unique index; the 022 full-payment flow will
     * pass {@code false}.
     */
    public static Payment of(Debt debt, BigDecimal amount, LocalDate date, Long registeredByUserId,
            String receiverName, UUID idempotencyKey, boolean legacy) {
        Payment payment = new Payment();
        payment.groupId = debt.getGroupId();
        payment.debtId = debt.getId();
        payment.payerType = debt.getDebtorType();
        payment.payerUserId = debt.getDebtorUserId();
        payment.payerSimulatedProfileId = debt.getDebtorSimulatedProfileId();
        payment.payerName = debt.getDebtorName();
        payment.receiverUserId = debt.getCreditorUserId();
        payment.receiverName = receiverName;
        payment.registeredByUserId = registeredByUserId;
        payment.amount = amount;
        payment.currency = debt.getCurrency();
        payment.paymentDate = date;
        payment.idempotencyKey = idempotencyKey;
        payment.status = PaymentStatus.CONFIRMED;
        payment.legacy = legacy;
        return payment;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getDebtId() {
        return debtId;
    }

    public ParticipantType getPayerType() {
        return payerType;
    }

    public Long getPayerUserId() {
        return payerUserId;
    }

    public Long getPayerSimulatedProfileId() {
        return payerSimulatedProfileId;
    }

    public String getPayerName() {
        return payerName;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public Long getRegisteredByUserId() {
        return registeredByUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Long getDecidedByUserId() {
        return decidedByUserId;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public boolean isLegacy() {
        return legacy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
