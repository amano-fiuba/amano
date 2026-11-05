package com.amano.backend.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;

/**
 * Unit tests of {@link FriendService} (features 013 and 016). The repository
 * query owns the list filtering and ordering (verified against PostgreSQL in
 * {@link FriendListIT}); here we verify session usage, direct-debt blocking and
 * the conditional deletion.
 */
class FriendServiceTest {

    private FriendshipRequestRepository requestRepository;
    private DirectDebtPort directDebtPort;
    private FriendService service;

    @BeforeEach
    void setUp() {
        requestRepository = Mockito.mock(FriendshipRequestRepository.class);
        directDebtPort = Mockito.mock(DirectDebtPort.class);
        service = new FriendService(requestRepository, directDebtPort);
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

    @Test
    void eligibilityIsAllowedWithoutDirectDebt() {
        when(requestRepository.existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED))
                .thenReturn(true);
        when(directDebtPort.hasPendingDirectDebt(9L, 12L)).thenReturn(false);

        DeletionEligibilityResponse eligibility =
                service.checkDeletionEligibility(9L, "12");

        assertThat(eligibility.canDelete()).isTrue();
        assertThat(eligibility.reason()).isNull();
        verify(requestRepository).existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED);
        verify(directDebtPort).hasPendingDirectDebt(9L, 12L);
        verifyNoMoreInteractions(requestRepository);
    }

    @Test
    void eligibilityIsBlockedByPendingDirectDebt() {
        when(requestRepository.existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED))
                .thenReturn(true);
        when(directDebtPort.hasPendingDirectDebt(9L, 12L)).thenReturn(true);

        DeletionEligibilityResponse eligibility =
                service.checkDeletionEligibility(9L, "12");

        assertThat(eligibility.canDelete()).isFalse();
        assertThat(eligibility.reason()).isEqualTo(PendingDirectDebtException.MESSAGE);
    }

    @Test
    void eligibilityOfANonFriendIsNeutral404WithoutCheckingDebt() {
        when(requestRepository.existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED))
                .thenReturn(false);

        assertThatThrownBy(() -> service.checkDeletionEligibility(9L, "12"))
                .isInstanceOf(FriendNotFoundException.class);

        verifyNoInteractions(directDebtPort);
    }

    @Test
    void deletesTheAcceptedFriendshipOfTheSessionUser() {
        when(requestRepository.existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED))
                .thenReturn(true);
        when(directDebtPort.hasPendingDirectDebt(9L, 12L)).thenReturn(false);
        when(requestRepository.deleteAcceptedBetween(9L, 12L)).thenReturn(1);

        service.deleteFriend(9L, "12");

        verify(requestRepository).deleteAcceptedBetween(9L, 12L);
    }

    @Test
    void doesNotDeleteWhenThereIsAPendingDirectDebt() {
        when(requestRepository.existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED))
                .thenReturn(true);
        when(directDebtPort.hasPendingDirectDebt(9L, 12L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteFriend(9L, "12"))
                .isInstanceOf(PendingDirectDebtException.class);

        verify(requestRepository, never()).deleteAcceptedBetween(anyLong(), anyLong());
    }

    @Test
    void deletingANonFriendIsNeutral404() {
        when(requestRepository.existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED))
                .thenReturn(false);

        assertThatThrownBy(() -> service.deleteFriend(9L, "12"))
                .isInstanceOf(FriendNotFoundException.class);

        verify(requestRepository, never()).deleteAcceptedBetween(anyLong(), anyLong());
        verifyNoInteractions(directDebtPort);
    }

    @Test
    void concurrentDeletionThatAffectsNoRowIsNeutral404() {
        when(requestRepository.existsBetween(9L, 12L, FriendshipRequestStatus.ACCEPTED))
                .thenReturn(true);
        when(directDebtPort.hasPendingDirectDebt(9L, 12L)).thenReturn(false);
        when(requestRepository.deleteAcceptedBetween(9L, 12L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteFriend(9L, "12"))
                .isInstanceOf(FriendNotFoundException.class);
    }

    @Test
    void invalidFriendIdsAreRejectedBeforeAnyQuery() {
        for (String invalid : List.of("", "abc", "0", "-3")) {
            assertThatThrownBy(() -> service.deleteFriend(9L, invalid))
                    .isInstanceOf(FieldValidationException.class);
        }

        verifyNoInteractions(requestRepository, directDebtPort);
    }
}
