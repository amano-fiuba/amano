package com.amano.backend.groups;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * User-group membership (table {@code group_members}, data-model.md). The role
 * lives in this relation, not on the user; only {@code ADMIN} and {@code MEMBER}
 * exist (same value set as the DB CHECK, V7). Simulated profiles never get rows
 * here (they have no user account). {@code leftAt} is the soft baja: the row is
 * preserved so prior expenses and history entries stay traceable; active
 * membership = {@code leftAt} IS NULL.
 */
@Entity
@Table(name = "group_members")
public class GroupMember {

    /** Composite PK (user_id, group_id) matching V7. */
    @Embeddable
    public static class GroupMemberId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "group_id", nullable = false)
        private Long groupId;

        public GroupMemberId() {
        }

        public GroupMemberId(Long userId, Long groupId) {
            this.userId = userId;
            this.groupId = groupId;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getGroupId() {
            return groupId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GroupMemberId other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(groupId, other.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, groupId);
        }
    }

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "role", nullable = false)
    private String role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    /** Null while the user is an active member; set on leave/removal. */
    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    public GroupMember() {
    }

    public GroupMember(Long userId, Long groupId, String role) {
        this.id = new GroupMemberId(userId, groupId);
        this.role = role;
    }

    public GroupMemberId getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(OffsetDateTime leftAt) {
        this.leftAt = leftAt;
    }
}
