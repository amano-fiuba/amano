package com.amano.backend.groupexpense;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Share of one participant in a group expense (table
 * {@code group_expense_shares}, 017 data-model.md). Exactly one of
 * {@code userId}/{@code simulatedProfileId} identifies the participant; the
 * profile id becomes {@code null} if the profile is later deleted, and the
 * name is a copy taken when the expense was registered (research.md D6).
 * Immutable once saved.
 */
@Entity
@Table(name = "group_expense_shares")
public class GroupExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    /** Order of the request; decides who gets the remainder (research.md D4). */
    @Column(name = "position", nullable = false)
    private short position;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 10)
    private ParticipantType participantType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "simulated_profile_id")
    private Long simulatedProfileId;

    @Column(name = "participant_name", nullable = false)
    private String participantName;

    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    protected GroupExpenseShare() {
    }

    private GroupExpenseShare(Long expenseId, int position, ParticipantType type, Long userId,
            Long simulatedProfileId, String name, BigDecimal amount) {
        this.expenseId = expenseId;
        this.position = (short) position;
        this.participantType = type;
        this.userId = userId;
        this.simulatedProfileId = simulatedProfileId;
        this.participantName = name;
        this.amount = amount;
    }

    /** Share of a registered member. */
    public static GroupExpenseShare registered(Long expenseId, int position, Long userId, String name,
            BigDecimal amount) {
        return new GroupExpenseShare(expenseId, position, ParticipantType.REGISTERED, userId, null, name,
                amount);
    }

    /** Share of a simulated profile of the group. */
    public static GroupExpenseShare simulated(Long expenseId, int position, Long profileId, String name,
            BigDecimal amount) {
        return new GroupExpenseShare(expenseId, position, ParticipantType.SIMULATED, null, profileId, name,
                amount);
    }

    /** True when this share belongs to the registered user {@code payerUserId}. */
    public boolean isOf(Long payerUserId) {
        return participantType == ParticipantType.REGISTERED && userId != null && userId.equals(payerUserId);
    }

    public Long getId() {
        return id;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public int getPosition() {
        return position;
    }

    public ParticipantType getParticipantType() {
        return participantType;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSimulatedProfileId() {
        return simulatedProfileId;
    }

    public String getParticipantName() {
        return participantName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
