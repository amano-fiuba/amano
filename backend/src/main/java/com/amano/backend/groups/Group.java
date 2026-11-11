package com.amano.backend.groups;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.amano.backend.user.Currency;
import com.amano.backend.user.CurrencyConversion;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Group aggregate root (table {@code groups}, data-model.md). The name is only
 * required and non-empty (FR-001, decision 2026-10-27: no uniqueness nor length
 * limit); business validation lives in the service layer (constitution VIII).
 */
@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    /** Optional presentation text; null when not provided. */
    @Column(name = "description")
    private String description;

    /** Optional photo reference (validated the same way as avatars, US8). */
    @Column(name = "photo")
    private String photo;

    /**
     * Currency of the group (022). The column is NOT NULL in V17 and existing
     * groups default to ARS; the field also defaults so a brand-new group gets a
     * valid value while the create form does not expose the choice yet.
     */
    @Convert(converter = CurrencyConversion.class)
    @Column(name = "primary_currency", nullable = false, length = 3)
    private Currency primaryCurrency = Currency.ARS;

    /**
     * Days after the expense date at which a PENDING debt becomes OVERDUE (022,
     * D7). Null means it never becomes overdue.
     */
    @Column(name = "debt_due_days")
    private Integer debtDueDays;

    /**
     * 025: days before the deadline at which the reminder is sent (7, 5, 3 or
     * 1). The DB column is NOT NULL DEFAULT 5, but the Java default is set too
     * because the JPA INSERT does not apply the DB default.
     */
    @Column(name = "reminder_send_days", nullable = false)
    private Integer reminderSendDays = 5;

    /**
     * 025: optional custom message that accompanies the debt data in the next
     * reminders. Null (or blank, normalized by the service) uses the default.
     */
    @Column(name = "reminder_message", length = 500)
    private String reminderMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public Currency getPrimaryCurrency() {
        return primaryCurrency;
    }

    public void setPrimaryCurrency(Currency primaryCurrency) {
        this.primaryCurrency = primaryCurrency;
    }

    public Integer getDebtDueDays() {
        return debtDueDays;
    }

    public void setDebtDueDays(Integer debtDueDays) {
        this.debtDueDays = debtDueDays;
    }

    public Integer getReminderSendDays() {
        return reminderSendDays;
    }

    public void setReminderSendDays(Integer reminderSendDays) {
        this.reminderSendDays = reminderSendDays;
    }

    public String getReminderMessage() {
        return reminderMessage;
    }

    public void setReminderMessage(String reminderMessage) {
        this.reminderMessage = reminderMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
