package com.amano.backend.income;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
 * A personal income (table {@code incomes}, specs/032-income-management/data-model.md).
 * It belongs to a single person ({@link #userId}) and is immutable once registered
 * (FR-006): the setters are package-private, so only the income service can fill a
 * new instance and nothing outside the package can change a persisted one. Pure
 * persistence mapping: normalization and business rules live in the service layer.
 */
@Entity
@Table(name = "incomes")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Owner, always the session principal; never provided by the client. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Exact monetary value; NUMERIC(11,2) in the database, never floating point. */
    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Convert(converter = CurrencyConversion.class)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "income_date", nullable = false)
    private LocalDate incomeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private IncomeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private IncomePaymentMethod paymentMethod;

    /** Optional; stored normalized and {@code null} when absent. */
    @Column(name = "description", length = 200)
    private String description;

    /** Identifier of the save operation, unique per user. */
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDate getIncomeDate() {
        return incomeDate;
    }

    public IncomeCategory getCategory() {
        return category;
    }

    public IncomePaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getDescription() {
        return description;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    void setUserId(Long userId) {
        this.userId = userId;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    void setCurrency(Currency currency) {
        this.currency = currency;
    }

    void setIncomeDate(LocalDate incomeDate) {
        this.incomeDate = incomeDate;
    }

    void setCategory(IncomeCategory category) {
        this.category = category;
    }

    void setPaymentMethod(IncomePaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    void setDescription(String description) {
        this.description = description;
    }

    void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
