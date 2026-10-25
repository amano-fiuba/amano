package com.amano.backend.expense;

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
 * A personal expense (table {@code expenses}, data-model.md). It belongs to a
 * single creator ({@link #userId}) and has no participants, splits, debts,
 * friends or groups (FR-006). Pure persistence mapping: normalization and
 * business rules live in the service layer.
 *
 * <p>Feature 009 adds the recurrence columns (specs/009-pause-recurring-expense,
 * data-model.md). One row is either a one-time expense, a recurring <em>series</em>
 * (frequency + status, plus a next date while not finished), an automatically
 * generated <em>occurrence</em> (source id of its series) or a legacy 007
 * recurring expense without frequency (all recurrence columns null).
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Creator, always the session principal — never provided by the client (FR-003/FR-005). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Exact monetary value; NUMERIC(11,2) in the database, never floating point (FR-023). */
    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Convert(converter = CurrencyConversion.class)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ExpenseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /** Optional; stored normalized and {@code null} when absent (FR-012). */
    @Column(name = "description", length = 200)
    private String description;

    /** Identifier of the save operation, unique per user (FR-027). */
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Series only (009, FR-001); null for one-time expenses, occurrences and legacy rows. */
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", length = 10)
    private RecurrenceFrequency recurrenceFrequency;

    /** Series only (009, FR-007); present exactly when the frequency is. */
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_status", length = 10)
    private RecurrenceStatus recurrenceStatus;

    /** Next date to generate; set while ACTIVE/PAUSED, null once FINISHED (FR-008/FR-009). */
    @Column(name = "recurrence_next_date")
    private LocalDate recurrenceNextDate;

    /** Series an occurrence was generated from (FR-005); plain id to avoid loading it. */
    @Column(name = "recurrence_source_id")
    private Long recurrenceSourceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public ExpenseType getType() {
        return type;
    }

    public void setType(ExpenseType type) {
        this.type = type;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public RecurrenceFrequency getRecurrenceFrequency() {
        return recurrenceFrequency;
    }

    public void setRecurrenceFrequency(RecurrenceFrequency recurrenceFrequency) {
        this.recurrenceFrequency = recurrenceFrequency;
    }

    public RecurrenceStatus getRecurrenceStatus() {
        return recurrenceStatus;
    }

    public void setRecurrenceStatus(RecurrenceStatus recurrenceStatus) {
        this.recurrenceStatus = recurrenceStatus;
    }

    public LocalDate getRecurrenceNextDate() {
        return recurrenceNextDate;
    }

    public void setRecurrenceNextDate(LocalDate recurrenceNextDate) {
        this.recurrenceNextDate = recurrenceNextDate;
    }

    public Long getRecurrenceSourceId() {
        return recurrenceSourceId;
    }

    public void setRecurrenceSourceId(Long recurrenceSourceId) {
        this.recurrenceSourceId = recurrenceSourceId;
    }

    /** A recurring series registered since 009 (has a frequency). */
    public boolean isRecurringSeries() {
        return recurrenceFrequency != null;
    }
}
