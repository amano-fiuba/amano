package com.amano.backend.user;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.friendship.FriendshipRequest;
import com.amano.backend.friendship.FriendshipRequestRepository;
import com.amano.backend.friendship.FriendshipRequestStatus;

/**
 * User search (feature 008): validates the term on the server (FR-004/FR-019,
 * the UI check is not enough — constitution VIII), normalizes it and maps the
 * matches to the minimal DTO. Invalid terms never reach the database.
 */
@Service
public class UserSearchService {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;
    public static final int MAX_RESULTS = 20;

    public static final String TOO_SHORT_MESSAGE = "Ingresá al menos 2 caracteres para buscar";
    public static final String TOO_LONG_MESSAGE = "La búsqueda no puede superar los 100 caracteres";

    private final UserRepository userRepository;
    private final FriendshipRequestRepository friendshipRequestRepository;
    private final AvatarVisibility avatarVisibility;

    public UserSearchService(UserRepository userRepository,
            FriendshipRequestRepository friendshipRequestRepository,
            AvatarVisibility avatarVisibility) {
        this.userRepository = userRepository;
        this.friendshipRequestRepository = friendshipRequestRepository;
        this.avatarVisibility = avatarVisibility;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResult> search(Long currentUserId, String rawTerm) {
        String term = rawTerm == null ? "" : rawTerm.strip();
        if (term.length() < MIN_LENGTH) {
            throw new FieldValidationException(Map.of("q", TOO_SHORT_MESSAGE));
        }
        if (term.length() > MAX_LENGTH) {
            throw new FieldValidationException(Map.of("q", TOO_LONG_MESSAGE));
        }
        String normalized = term.toLowerCase(Locale.ROOT);
        List<User> users = userRepository
                .searchVerified(normalized, currentUserId, PageRequest.of(0, MAX_RESULTS));
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, String> statusByUser = new HashMap<>();
        for (FriendshipRequest request : friendshipRequestRepository.findActiveBetween(
                currentUserId, userIds)) {
            boolean sentByMe = currentUserId.equals(request.getSenderId());
            Long other = sentByMe ? request.getRecipientId() : request.getSenderId();
            String status = request.getStatus() == FriendshipRequestStatus.ACCEPTED
                    ? UserSearchResult.FRIENDS
                    : sentByMe ? UserSearchResult.PENDING_SENT : UserSearchResult.PENDING_RECEIVED;
            statusByUser.merge(other, status, (a, b) -> UserSearchResult.FRIENDS.equals(a) ? a : b);
        }
        Set<Long> withAvatar = userRepository.findIdsWithAvatar(userIds);
        Set<Long> visible = avatarVisibility.visibleAmong(currentUserId, userIds);
        return users.stream()
                .map(user -> UserSearchResult.from(user,
                        statusByUser.getOrDefault(user.getId(), UserSearchResult.NONE),
                        withAvatar.contains(user.getId()) && visible.contains(user.getId())))
                .toList();
    }
}
