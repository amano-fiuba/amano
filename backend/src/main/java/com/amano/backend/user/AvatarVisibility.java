package com.amano.backend.user;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.friendship.FriendshipRequestRepository;
import com.amano.backend.friendship.FriendshipRequestStatus;
import com.amano.backend.groups.GroupMemberRepository;

/**
 * Who can see a custom avatar: its owner, their friends (ACCEPTED request in
 * either direction), the accounts sharing an active group with them and whoever
 * received a pending request from them, which are the screens that render
 * someone else's avatar. A pending request opens the sender's avatar to the
 * recipient only: sending a request never reveals the recipient's. Ids are enumerable, so a
 * verified session alone is not enough.
 */
@Component
public class AvatarVisibility {

    private final FriendshipRequestRepository friendshipRequestRepository;
    private final GroupMemberRepository groupMemberRepository;

    public AvatarVisibility(FriendshipRequestRepository friendshipRequestRepository,
            GroupMemberRepository groupMemberRepository) {
        this.friendshipRequestRepository = friendshipRequestRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Transactional(readOnly = true)
    public boolean canView(Long viewerId, Long ownerId) {
        return viewerId.equals(ownerId)
                || friendshipRequestRepository.existsBetween(viewerId, ownerId,
                        FriendshipRequestStatus.ACCEPTED)
                || groupMemberRepository.shareActiveGroup(viewerId, ownerId)
                || friendshipRequestRepository.existsBySenderIdAndRecipientIdAndStatus(ownerId,
                        viewerId, FriendshipRequestStatus.PENDING);
    }
}
