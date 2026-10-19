package com.amano.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@code user_avatars}. Existence of the row is the source of the
 * computed attribute {@code hasCustomAvatar} (data-model.md, "Reglas derivadas").
 */
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
