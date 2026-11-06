package com.amano.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import com.amano.backend.friendship.FriendshipRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.amano.backend.common.FieldValidationException;

/** Unit tests of UserSearchService (feature 008): validation, normalization, mapping. */
class UserSearchServiceTest {

    private UserRepository userRepository;
    private FriendshipRequestRepository friendshipRequestRepository;
    private UserSearchService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        friendshipRequestRepository = Mockito.mock(FriendshipRequestRepository.class);
        service = new UserSearchService(userRepository, friendshipRequestRepository);
    }

    private static User user(long id, String firstName, String lastName, String email) {
        User user = new User();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        return user;
    }

    @Test
    void termIsTrimmedAndLowercasedAndCappedAt20() {
        when(userRepository.searchVerified(anyString(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        service.search(7L, "  AnA ");

        verify(userRepository).searchVerified(eq("ana"), eq(7L), eq(PageRequest.of(0, 20)));
    }

    @Test
    void resultsExposeOnlyTheAllowedFields() {
        User ana = user(3L, "Ana", "Pérez", "ana@example.com");
        ana.setCbu("0000003100010000000001");
        when(userRepository.searchVerified(anyString(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(ana));

        List<UserSearchResult> results = service.search(7L, "an");

        assertThat(results).containsExactly(new UserSearchResult(3L, "Ana", "Pérez", "ana@example.com", "NONE"));
    }

    @Test
    void missingLastNameStaysNull() {
        User juan = user(4L, "Juan", null, "juan@example.com");
        when(userRepository.searchVerified(anyString(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(juan));

        assertThat(service.search(7L, "ju").getFirst().lastName()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   ", "a", "  a  " })
    void tooShortTermsAreRejectedWithoutQuerying(String term) {
        assertThatThrownBy(() -> service.search(7L, term))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields())
                                .containsEntry("q", UserSearchService.TOO_SHORT_MESSAGE));
        verifyNoInteractions(userRepository);
    }

    @Test
    void twoCharactersAreEnough() {
        when(userRepository.searchVerified(anyString(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.search(7L, "ab")).isEmpty();
    }

    @Test
    void moreThan100CharactersAreRejectedWithoutQuerying() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> service.search(7L, tooLong))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields())
                                .containsEntry("q", UserSearchService.TOO_LONG_MESSAGE));
        verifyNoInteractions(userRepository);
    }

    @Test
    void exactly100CharactersAreAccepted() {
        when(userRepository.searchVerified(anyString(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.search(7L, "a".repeat(100))).isEmpty();
    }

    @Test
    void resultsCarryTheFriendshipStatusWithTheCaller() {
        when(userRepository.searchVerified(anyString(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(user(3L, "Ana", "Pérez", "ana@example.com"),
                        user(4L, "Bruno", null, "bruno@example.com"),
                        user(5L, "Carla", null, "carla@example.com")));
        com.amano.backend.friendship.FriendshipRequest fromAna = request(3L, 1L,
                com.amano.backend.friendship.FriendshipRequestStatus.PENDING);
        com.amano.backend.friendship.FriendshipRequest toBruno = request(1L, 4L,
                com.amano.backend.friendship.FriendshipRequestStatus.PENDING);
        com.amano.backend.friendship.FriendshipRequest withCarla = request(5L, 1L,
                com.amano.backend.friendship.FriendshipRequestStatus.ACCEPTED);
        when(friendshipRequestRepository.findActiveBetween(eq(1L), any()))
                .thenReturn(List.of(fromAna, toBruno, withCarla));

        List<UserSearchResult> results = service.search(1L, "an");

        assertThat(results).extracting(UserSearchResult::friendshipStatus)
                .containsExactly("PENDING_RECEIVED", "PENDING_SENT", "FRIENDS");
    }

    private static com.amano.backend.friendship.FriendshipRequest request(Long sender, Long recipient,
            com.amano.backend.friendship.FriendshipRequestStatus status) {
        com.amano.backend.friendship.FriendshipRequest request = Mockito
                .mock(com.amano.backend.friendship.FriendshipRequest.class);
        when(request.getSenderId()).thenReturn(sender);
        when(request.getRecipientId()).thenReturn(recipient);
        when(request.getStatus()).thenReturn(status);
        return request;
    }
}
