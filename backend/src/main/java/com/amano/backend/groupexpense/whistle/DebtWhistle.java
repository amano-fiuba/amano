package com.amano.backend.groupexpense.whistle;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One whistle attempt (table {@code debt_whistles}, 030 data-model.md). It is
 * created {@code SENDING} inside the claim transaction and moved to
 * {@code SENT} or {@code FAILED} by the conditional updates of
 * {@link DebtWhistleRepository}. {@code createdAt} comes from the application
 * clock (not the DB default) so the 24 h window follows the same clock as the
 * rest of the business rules.
 */
@Entity
@Table(name = "debt_whistles")
public class DebtWhistle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "debt_id", nullable = false)
    private Long debtId;

    @Column(name = "creditor_user_id", nullable = false)
    private Long creditorUserId;

    @Column(name = "debtor_user_id", nullable = false)
    private Long debtorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DebtWhistleStatus status = DebtWhistleStatus.SENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DebtWhistle() {
    }

    /** New {@code SENDING} attempt of {@code creditorUserId} to {@code debtorUserId}. */
    public static DebtWhistle sending(Long groupId, Long debtId, Long creditorUserId, Long debtorUserId,
            OffsetDateTime now) {
        DebtWhistle whistle = new DebtWhistle();
        whistle.groupId = groupId;
        whistle.debtId = debtId;
        whistle.creditorUserId = creditorUserId;
        whistle.debtorUserId = debtorUserId;
        whistle.createdAt = now;
        whistle.updatedAt = now;
        return whistle;
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

    public Long getCreditorUserId() {
        return creditorUserId;
    }

    public Long getDebtorUserId() {
        return debtorUserId;
    }

    public DebtWhistleStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
