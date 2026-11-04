package com.amano.backend.groups;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * History event (table {@code group_activity}, data-model.md). Standalone row
 * per the preservation pattern: it references only {@code groupId} and persists
 * even when the related member/expense/payment is later modified or deleted.
 * {@code eventType} values match the DB CHECK in V10 (see {@link GroupActivityType}).
 */
@Entity
@Table(name = "group_activity")
public class GroupActivity {

    /** Same value set as the V10 CHECK; kept in sync everywhere in the module. */
    public enum GroupActivityType {
        MEMBER_ADDED,
        MEMBER_REMOVED,
        MEMBER_LEFT,
        PERMISSION_CHANGED,
        PHOTO_CHANGED,
        EXPENSE_CREATED,
        PAYMENT_MADE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** Member who performed the action; null for system-originated events. */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    /** Optional free-form details persisted as JSON text. */
    @Column(name = "details_json")
    private String detailsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public GroupActivity() {
    }

    public GroupActivity(Long groupId, GroupActivityType eventType, Long actorUserId, String detailsJson) {
        this.groupId = groupId;
        this.eventType = eventType.name();
        this.actorUserId = actorUserId;
        this.detailsJson = detailsJson;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
