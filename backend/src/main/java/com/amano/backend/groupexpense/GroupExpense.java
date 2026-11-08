package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.amano.backend.expense.ExpenseCategory;
import com.amano.backend.expense.ExpenseType;
import com.amano.backend.expense.PaymentMethod;
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
 * Minimal group expense (table {@code group_expenses}, 017 data-model.md): a
 * registered payer, an equal division among its shares and no edition or
 * deletion. Kept apart from the personal {@code expenses} of 007/009
 * (research.md D2). Pure persistence mapping: rules live in
 * {@link GroupExpenseService}.
 */
@Entity
@Table(name = "group_expenses")
public class GroupExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** Who registered it, always the session principal (actor of the history event). */
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "payer_user_id", nullable = false)
    private Long payerUserId;

    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Convert(converter = CurrencyConversion.class)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    /** 022 D8: amount converted to the group currency (null until converted). */
    @Column(name = "converted_amount", precision = 11, scale = 2)
    private BigDecimal convertedAmount;

    /** 022 D8: rate applied from the expense currency to the group currency. */
    @Column(name = "exchange_rate", precision = 18, scale = 8)
    private BigDecimal exchangeRate;

    /** 022 D8: date of the historical quote. */
    @Column(name = "rate_date")
    private LocalDate rateDate;

    /** 022 D8: source of the quote (for example {@code BCRA}). */
    @Column(name = "rate_source", length = 40)
    private String rateSource;

    /** 022 D2/T066: how the expense was split. Existing and omitted payloads stay EQUAL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "split_method", nullable = false, length = 10)
    private SplitMethod splitMethod = SplitMethod.EQUAL;

    /** 027 FR-020: 007 classification. Null only for expenses created before 027. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private ExpenseCategory category;

    /** 027 FR-020: informative only; a RECURRING shared expense is never scheduled. */
    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", length = 20)
    private ExpenseType expenseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Long getPayerUserId() {
        return payerUserId;
    }

    public void setPayerUserId(Long payerUserId) {
        this.payerUserId = payerUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    public void setConvertedAmount(BigDecimal convertedAmount) {
        this.convertedAmount = convertedAmount;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }

    public String getRateSource() {
        return rateSource;
    }

    public void setRateSource(String rateSource) {
        this.rateSource = rateSource;
    }

    public SplitMethod getSplitMethod() {
        return splitMethod;
    }

    public void setSplitMethod(SplitMethod splitMethod) {
        this.splitMethod = splitMethod;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public void setExpenseType(ExpenseType expenseType) {
        this.expenseType = expenseType;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
