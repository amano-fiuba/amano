package com.amano.backend.friendship;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Friends list of the session user (feature 013). Read-only: a friendship is an
 * ACCEPTED friendship request (011/012), so this only queries; the ordering and
 * the "other account" resolution live in the repository query (research.md D3/D4).
 */
@Service
public class FriendService {

    private final FriendshipRequestRepository requestRepository;

    public FriendService(FriendshipRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    /** All friends of {@code userId}, alphabetically (FR-001..FR-005). */
    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(Long userId) {
        return requestRepository.findFriendsOf(userId);
    }
}
