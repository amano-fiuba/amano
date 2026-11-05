package com.amano.backend.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests of {@link FriendService} (feature 013): it only asks the repository
 * for the session user's friends and returns them untouched (the query owns the
 * filter and the ordering, verified against PostgreSQL in {@link FriendListIT}).
 */
class FriendServiceTest {

    private FriendshipRequestRepository requestRepository;
    private FriendService service;

    @BeforeEach
    void setUp() {
        requestRepository = Mockito.mock(FriendshipRequestRepository.class);
        service = new FriendService(requestRepository);
    }

    @Test
    void listsTheFriendsOfTheSessionUserInRepositoryOrder() {
        List<FriendResponse> friends = List.of(
                new FriendResponse(4L, "Ana", "Paz", "ana@example.com",
                        OffsetDateTime.parse("2026-10-31T10:00:00Z"), null),
                new FriendResponse(2L, "Bruno", null, "bruno@example.com",
                        OffsetDateTime.parse("2026-10-30T10:00:00Z"), null));
        when(requestRepository.findFriendsOf(9L)).thenReturn(friends);

        assertThat(service.listFriends(9L)).containsExactlyElementsOf(friends);
        verify(requestRepository).findFriendsOf(9L);
        verifyNoMoreInteractions(requestRepository);
    }

    @Test
    void noFriendsIsAnEmptyList() {
        when(requestRepository.findFriendsOf(9L)).thenReturn(List.of());

        assertThat(service.listFriends(9L)).isEmpty();
    }
}
