package com.amano.backend.groups.port;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.friendship.FriendResponse;
import com.amano.backend.friendship.FriendshipRequestRepository;

/**
 * Real implementation of {@link FriendshipPort} over the friendships of
 * 011–013: a friend is the other account of an {@code ACCEPTED} row of
 * {@code friendship_requests}, in either direction. Reuses the query of the
 * friends list ({@code findFriendsOf}, 013 research.md D3), so "Agregar
 * miembros" and "Mis amigos" always agree. Replaces the old
 * {@code NoOpFriendshipPort} stub of 010, which ignored real friendships.
 */
@Component
public class AcceptedFriendshipPort implements FriendshipPort {

    private final FriendshipRequestRepository friendshipRequestRepository;

    public AcceptedFriendshipPort(FriendshipRequestRepository friendshipRequestRepository) {
        this.friendshipRequestRepository = friendshipRequestRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> friendEmailsOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return friendshipRequestRepository.findFriendsOf(userId).stream()
                .map(FriendResponse::email)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFriend(Long userId, String otherEmail) {
        if (userId == null || otherEmail == null || otherEmail.isBlank()) {
            return false;
        }
        String normalized = otherEmail.trim().toLowerCase(Locale.ROOT);
        return friendEmailsOf(userId).stream()
                .anyMatch(email -> email != null && email.toLowerCase(Locale.ROOT).equals(normalized));
    }
}
