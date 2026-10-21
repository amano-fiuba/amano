package com.amano.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@code user_avatars}. Existence of the row is the source of the
 * computed attribute {@code hasCustomAvatar} (data-model.md, "Reglas derivadas").
 */
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {

    /**
     * Bulk delete by user id (feature 005, FR-016, idempotent): removing the
     * ENTITY through the entity lifecycle makes the lazy {@code User.avatar}
     * association reference a transient instance at flush time, so the row is
     * deleted straight with JPQL and the persistence context is cleared.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserAvatar a where a.id = :userId")
    int deleteRowForUser(@Param("userId") Long userId);
}
