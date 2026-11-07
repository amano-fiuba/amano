package com.amano.backend.groupexpense.reminder;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Scheduled reminder (table {@code reminders}, 025 data-model.md). A single
 * table with a {@link ReminderKind} discriminator holds both the debt reminder
 * and the payer due notice: they share the scheduling, claim and lifecycle.
 * The {@code subject}/{@code body} snapshots are only filled when the reminder
 * is sent, so later configuration changes never rewrite already sent mails.
 */
@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ReminderKind kind;

    @Column(name = "debt_id")
    private Long debtId;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "send_date", nullable = false)
    private LocalDate sendDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.SCHEDULED;

    /** Set when a run claims the row; a claimed row is never retried. */
    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Reminder() {
    }

    /** New {@code SCHEDULED} reminder for a debt. */
    public static Reminder scheduledForDebt(Long groupId, Long debtId, LocalDate sendDate) {
        Reminder reminder = new Reminder();
        reminder.groupId = groupId;
        reminder.kind = ReminderKind.DEBT;
        reminder.debtId = debtId;
        reminder.sendDate = sendDate;
        reminder.status = ReminderStatus.SCHEDULED;
        return reminder;
    }

    /** New {@code SCHEDULED} due notice for the payer of an expense. */
    public static Reminder scheduledForPayerNotice(Long groupId, Long expenseId, LocalDate sendDate) {
        Reminder reminder = new Reminder();
        reminder.groupId = groupId;
        reminder.kind = ReminderKind.PAYER_NOTICE;
        reminder.expenseId = expenseId;
        reminder.sendDate = sendDate;
        reminder.status = ReminderStatus.SCHEDULED;
        return reminder;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public ReminderKind getKind() {
        return kind;
    }

    public Long getDebtId() {
        return debtId;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public LocalDate getSendDate() {
        return sendDate;
    }

    public void setSendDate(LocalDate sendDate) {
        this.sendDate = sendDate;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public void setStatus(ReminderStatus status) {
        this.status = status;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(OffsetDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
