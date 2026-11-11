package com.amano.backend.groups.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.friendship.FriendResponse;
import com.amano.backend.friendship.FriendshipRequestRepository;

/**
 * Real {@link FriendshipPort}: friends are the ACCEPTED friendships of 011–013
 * (fix of the 010 stub). The SQL itself is covered by FriendListIT (013) and
 * GroupRealFriendshipIT.
 */
class AcceptedFriendshipPortTest {

    private FriendshipRequestRepository repository;
    private AcceptedFriendshipPort port;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(FriendshipRequestRepository.class);
        port = new AcceptedFriendshipPort(repository);
        when(repository.findFriendsOf(7L)).thenReturn(List.of(
                new FriendResponse(9L, "Beto", null, "beto@example.com", OffsetDateTime.now(), null, null),
                new FriendResponse(10L, "Carla", "Díaz", "Carla@Example.com", OffsetDateTime.now(), null, null)));
    }

    @Test
    void friendEmailsAreTheOtherAccountsOfAcceptedFriendships() {
        assertThat(port.friendEmailsOf(7L)).containsExactly("beto@example.com", "Carla@Example.com");
        assertThat(port.friendEmailsOf(null)).isEmpty();
    }

    @Test
    void isFriendComparesTrimmedEmailsIgnoringCase() {
        assertThat(port.isFriend(7L, " BETO@example.com ")).isTrue();
        assertThat(port.isFriend(7L, "carla@example.com")).isTrue();
        assertThat(port.isFriend(7L, "stranger@example.com")).isFalse();
    }

    @Test
    void missingInputIsNeverAFriendAndDoesNotQuery() {
        assertThat(port.isFriend(null, "beto@example.com")).isFalse();
        assertThat(port.isFriend(7L, null)).isFalse();
        assertThat(port.isFriend(7L, "  ")).isFalse();
        verify(repository, never()).findFriendsOf(any());
    }
}
