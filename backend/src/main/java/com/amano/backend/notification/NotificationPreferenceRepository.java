package com.amano.backend.notification;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** One preferences row per person (feature 035, plan D7). */
public interface NotificationPreferenceRepository extends JpaRepository<UserNotificationPreferences, Long> {

    Optional<UserNotificationPreferences> findByUserId(Long userId);
}
