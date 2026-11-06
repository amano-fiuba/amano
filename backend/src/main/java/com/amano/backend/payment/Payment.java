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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
