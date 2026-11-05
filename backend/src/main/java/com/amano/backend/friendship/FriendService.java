package com.amano.backend.friendship;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;

/**
 * Friends list and friendship deletion of the session user (features 013 and
 * 016). A friendship is an ACCEPTED friendship request (011/012), so deleting it
 * is deleting that single row. Direct debts are consulted through
 * {@link DirectDebtPort}; group debts are deliberately not part of this rule
 * (feature 016, research.md D2/D3/D6).
 */
@Service
public class FriendService {

    public static final String FRIEND_INVALID_MESSAGE =
            "El identificador del amigo no es válido";

    private final FriendshipRequestRepository requestRepository;
    private final DirectDebtPort directDebtPort;

    public FriendService(FriendshipRequestRepository requestRepository,
            DirectDebtPort directDebtPort) {
        this.requestRepository = requestRepository;
        this.directDebtPort = directDebtPort;
    }

    /** All friends of {@code userId}, alphabetically (FR-001..FR-005 of 013). */
    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(Long userId) {
        return requestRepository.findFriendsOf(userId);
    }

    /**
     * Whether the session user can delete the friendship (feature 016, FR-003,
     * FR-011, FR-022). A missing friendship is a neutral 404; a pending direct
     * debt returns {@code canDelete = false} with the reason to show.
     */
    @Transactional(readOnly = true)
    public DeletionEligibilityResponse checkDeletionEligibility(Long userId, String friendIdRaw) {
        long friendId = parseFriendId(friendIdRaw);
        requireFriendship(userId, friendId);
        if (directDebtPort.hasPendingDirectDebt(userId, friendId)) {
            return DeletionEligibilityResponse.blocked(PendingDirectDebtException.MESSAGE);
        }
        return DeletionEligibilityResponse.allowed();
    }

    /**
     * Deletes the friendship between the session user and {@code friendId}
     * (feature 016, FR-007..FR-013, FR-025, FR-030). The direct-debt check is
     * repeated here so the rule holds even if it changed after the eligibility
     * query. The conditional delete keeps concurrent attempts consistent: the
     * loser affects zero rows and receives the neutral 404.
     */
    @Transactional
    public void deleteFriend(Long userId, String friendIdRaw) {
        long friendId = parseFriendId(friendIdRaw);
        requireFriendship(userId, friendId);
        if (directDebtPort.hasPendingDirectDebt(userId, friendId)) {
            throw new PendingDirectDebtException();
        }
        if (requestRepository.deleteAcceptedBetween(userId, friendId) == 0) {
            throw new FriendNotFoundException();
        }
    }

    private void requireFriendship(Long userId, long friendId) {
        if (!requestRepository.existsBetween(userId, friendId, FriendshipRequestStatus.ACCEPTED)) {
            throw new FriendNotFoundException();
        }
    }

    /** Non-numeric or non-positive path id is a 400 before any query (FR-024). */
    private static long parseFriendId(String friendIdRaw) {
        if (friendIdRaw == null || friendIdRaw.isBlank()) {
            throw new FieldValidationException(Map.of("friendId", FRIEND_INVALID_MESSAGE));
        }
        try {
            long friendId = Long.parseLong(friendIdRaw);
            if (friendId <= 0) {
                throw new FieldValidationException(Map.of("friendId", FRIEND_INVALID_MESSAGE));
            }
            return friendId;
        }
        catch (NumberFormatException exception) {
            throw new FieldValidationException(Map.of("friendId", FRIEND_INVALID_MESSAGE));
        }
    }
}
