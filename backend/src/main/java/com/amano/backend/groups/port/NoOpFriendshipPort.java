package com.amano.backend.groups.port;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link FriendshipPort} until the friends module
 * exists. Every caller shares the same configurable fixture list
 * (property {@code groups.friendship.stub-friend-emails}, default
 * {@code test@example.com}) — enough for automated tests and local dev to
 * create groups with an additional registered member (US1).
 */
@Component
public class NoOpFriendshipPort implements FriendshipPort {

    private final List<String> stubFriendEmails;

    public NoOpFriendshipPort(
            @Value("${groups.friendship.stub-friend-emails:test@example.com}") List<String> stubFriendEmails) {
        this.stubFriendEmails = stubFriendEmails;
    }

    @Override
    public List<String> friendEmailsOf(Long userId) {
        return List.copyOf(stubFriendEmails);
    }

    @Override
    public boolean isFriend(Long userId, String otherEmail) {
        return otherEmail != null && stubFriendEmails.stream().anyMatch(e -> e.equalsIgnoreCase(otherEmail.trim()));
    }
}
