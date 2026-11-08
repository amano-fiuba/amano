package com.amano.backend.groupexpense;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.friendship.FriendNotFoundException;
import com.amano.backend.friendship.FriendshipRequestRepository;
import com.amano.backend.friendship.FriendshipRequestStatus;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * 027 US4 (FR-030..FR-034): expenses shared with a friend. Checks the friendship
 * (the same ACCEPTED-request rule as 013/016) and the names of both people, then
 * delegates to {@link GroupExpenseService}, which keeps validation, split,
 * rounding and idempotency in one place.
 */
@Service
public class FriendExpenseService {

    private final FriendshipRequestRepository friendshipRequestRepository;
    private final UserRepository userRepository;
    private final GroupExpenseService groupExpenseService;

    public FriendExpenseService(FriendshipRequestRepository friendshipRequestRepository,
            UserRepository userRepository, GroupExpenseService groupExpenseService) {
        this.friendshipRequestRepository = friendshipRequestRepository;
        this.userRepository = userRepository;
        this.groupExpenseService = groupExpenseService;
    }

    @Transactional
    public GroupExpenseService.Registration create(Long callerId, Long friendId, UUID idempotencyKey,
            CreateGroupExpenseRequest request) {
        return groupExpenseService.createWithFriend(callerId, friendId, pairNames(callerId, friendId),
                idempotencyKey, request);
    }

    @Transactional(readOnly = true)
    public GroupExpenseService.Registration resolveConcurrentRegistration(Long callerId, Long friendId,
            UUID idempotencyKey, CreateGroupExpenseRequest request) {
        return groupExpenseService.resolveConcurrentFriendRegistration(callerId, friendId,
                pairNames(callerId, friendId), idempotencyKey, request);
    }

    @Transactional(readOnly = true)
    public GroupExpenseResponse get(Long callerId, Long friendId, Long expenseId) {
        requireFriendship(callerId, friendId);
        return groupExpenseService.getWithFriend(callerId, friendId, expenseId);
    }

    /** Names of the caller and the friend, after checking they are friends (FR-033: 404 otherwise). */
    private Map<Long, String> pairNames(Long callerId, Long friendId) {
        requireFriendship(callerId, friendId);
        User caller = userRepository.findById(callerId).orElseThrow(FriendNotFoundException::new);
        User friend = userRepository.findById(friendId).orElseThrow(FriendNotFoundException::new);
        return Map.of(callerId, nameOf(caller), friendId, nameOf(friend));
    }

    private void requireFriendship(Long callerId, Long friendId) {
        if (friendId == null || friendId.equals(callerId)
                || !friendshipRequestRepository.existsBetween(callerId, friendId, FriendshipRequestStatus.ACCEPTED)) {
            throw new FriendNotFoundException();
        }
    }

    private static String nameOf(User user) {
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }
}
