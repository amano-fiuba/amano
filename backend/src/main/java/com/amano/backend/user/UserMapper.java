package com.amano.backend.user;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile mapper (task T020). The computed attribute hasCustomAvatar is derived
 * EXCLUSIVELY here, from the existence of a row in user_avatars — no redundant
 * column is persisted (data-model.md "Reglas derivadas").
 *
 * Reads run in a read-only transaction because the lazy currencies collection
 * of the entity must initialize inside the same session (open-in-view disabled).
 */
@Component
public class UserMapper {

    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;

    public UserMapper(UserRepository userRepository, UserAvatarRepository userAvatarRepository) {
        this.userRepository = userRepository;
        this.userAvatarRepository = userAvatarRepository;
    }

    /** Loads the user fresh (attached) and maps its public representation, or null. */
    @Transactional(readOnly = true)
    public UserAccountResponse summaryOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> {
                    byte[] avatarData = avatarDataOf(user.getId());
                    return UserAccountResponse.from(user, avatarData != null,
                            avatarData == null ? null : UserAccountResponse.versionOf(avatarData));
                })
                .orElse(null);
    }

    private byte[] avatarDataOf(Long userId) {
        return userAvatarRepository.findById(userId)
                .map(UserAvatar::getData)
                .orElse(null);
    }
}
