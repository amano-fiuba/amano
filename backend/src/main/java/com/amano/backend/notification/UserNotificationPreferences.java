package com.amano.backend.notification;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Notification settings of one person (table {@code user_notification_preferences},
 * feature 035 data-model.md). One row per user; all six flags start enabled and
 * are created lazily by the preference service on first read or write.
 */
@Entity
@Table(name = "user_notification_preferences")
public class UserNotificationPreferences {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    @Column(name = "email", nullable = false)
    private boolean email = true;

    @Column(name = "reminders_whistles", nullable = false)
    private boolean remindersWhistles = true;

    @Column(name = "debt_due_collections", nullable = false)
    private boolean debtDueCollections = true;

    @Column(name = "group_activity", nullable = false)
    private boolean groupActivity = true;

    @Column(name = "friend_activity", nullable = false)
    private boolean friendActivity = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserNotificationPreferences() {
    }

    /** New defaults for {@code userId}: both channels and all four event types on. */
    public static UserNotificationPreferences defaultsFor(Long userId) {
        UserNotificationPreferences preferences = new UserNotificationPreferences();
        preferences.userId = userId;
        return preferences;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isInApp() {
        return inApp;
    }

    public void setInApp(boolean inApp) {
        this.inApp = inApp;
    }

    public boolean isEmail() {
        return email;
    }

    public void setEmail(boolean email) {
        this.email = email;
    }

    public boolean isRemindersWhistles() {
        return remindersWhistles;
    }

    public void setRemindersWhistles(boolean remindersWhistles) {
        this.remindersWhistles = remindersWhistles;
    }

    public boolean isDebtDueCollections() {
        return debtDueCollections;
    }

    public void setDebtDueCollections(boolean debtDueCollections) {
        this.debtDueCollections = debtDueCollections;
    }

    public boolean isGroupActivity() {
        return groupActivity;
    }

    public void setGroupActivity(boolean groupActivity) {
        this.groupActivity = groupActivity;
    }

    public boolean isFriendActivity() {
        return friendActivity;
    }

    public void setFriendActivity(boolean friendActivity) {
        this.friendActivity = friendActivity;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
