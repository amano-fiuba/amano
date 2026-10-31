package com.amano.backend.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of the respond flow of {@link FriendshipRequestService} (feature
 * 012): validations run before any query, the transition is the single
 * conditional UPDATE of T006, and the re-read after zero affected rows only
 * distinguishes 404 from 409 without writing anything.
 */
class FriendshipRequestServiceTest {

    private static final Long RESPONDER_ID = 8L;
    private static final Long SENDER_ID = 5L;
    private static final Long REQUEST_ID = 40L;

    private FriendshipRequestRepository requestRepository;
    private UserRepository userRepository;
    private FriendshipRequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = Mockito.mock(FriendshipRequestRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        service = new FriendshipRequestService(requestRepository, userRepository);
    }

    private static User sender() {
        User sender = new User();
        sender.setId(SENDER_ID);
        sender.setFirstName("Bruno");
        sender.setLastName(null);
        sender.setEmail("bruno@example.com");
        return sender;
    }

    private static FriendshipRequest pendingRequest() {
        FriendshipRequest request = new FriendshipRequest();
        request.setId(REQUEST_ID);
        request.setSenderId(SENDER_ID);
        request.setRecipientId(RESPONDER_ID);
        request.setStatus(FriendshipRequestStatus.PENDING);
        request.setCreatedAt(OffsetDateTime.parse("2026-10-30T14:05:00Z"));
        request.setRespondedAt(null);
        return request;
    }

    private static RespondFriendshipRequestRequest action(String value) {
        return new RespondFriendshipRequestRequest(value);
    }

    @Test
    void acceptingRegistersTheFinalStatusWithTheResponseDate() {
        FriendshipRequest pending = pendingRequest();
        when(requestRepository.respondPending(eq(REQUEST_ID), eq(RESPONDER_ID),
                eq(FriendshipRequestStatus.ACCEPTED), any(OffsetDateTime.class)))
                .thenReturn(1);
        FriendshipRequest answered = pending;
        answered.setStatus(FriendshipRequestStatus.ACCEPTED);
        answered.setRespondedAt(OffsetDateTime.now());
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(answered));
        when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender()));

        AnsweredFriendshipRequestResponse response = service.respond(RESPONDER_ID,
                String.valueOf(REQUEST_ID), action("ACCEPT"));

        assertThat(response.status()).isEqualTo(FriendshipRequestStatus.ACCEPTED);
        assertThat(response.respondedAt()).isNotNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.sender().email()).isEqualTo("bruno@example.com");
        assertThat(response.sender().lastName()).isNull();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectingMapsToTheRejectedStatusWithoutFriendshipRows() {
        FriendshipRequest answered = pendingRequest();
        answered.setStatus(FriendshipRequestStatus.REJECTED);
        answered.setRespondedAt(OffsetDateTime.now());
        when(requestRepository.respondPending(eq(REQUEST_ID), eq(RESPONDER_ID),
                eq(FriendshipRequestStatus.REJECTED), any(OffsetDateTime.class)))
                .thenReturn(1);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(answered));
        when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender()));

        AnsweredFriendshipRequestResponse response = service.respond(RESPONDER_ID,
                String.valueOf(REQUEST_ID), action("REJECT"));

        assertThat(response.status()).isEqualTo(FriendshipRequestStatus.REJECTED);
        verify(requestRepository, never()).save(any(FriendshipRequest.class));
    }

    @Test
    void anUnknownActionIsValidFieldErrorWithoutTouchingTheDatabase() {
        FieldValidationException exception = catchThrowableOfType(() -> service.respond(
                RESPONDER_ID, String.valueOf(REQUEST_ID), action("MAYBE")),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsEntry("action",
                FriendshipRequestAction.INVALID_ACTION_MESSAGE);
        verify(requestRepository, never()).respondPending(any(), any(), any(), any());
        verify(requestRepository, never()).findById(any());
    }

    @Test
    void aMissingBodyProducesTheActionFieldError() {
        FieldValidationException exception = catchThrowableOfType(() -> service.respond(
                RESPONDER_ID, String.valueOf(REQUEST_ID), null),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsEntry("action",
                FriendshipRequestAction.INVALID_ACTION_MESSAGE);
        verify(requestRepository, never()).respondPending(any(), any(), any(), any());
    }

    @Test
    void aNonNumericRequestIdIsAFieldErrorBeforeAnyQuery() {
        FieldValidationException exception = catchThrowableOfType(() -> service.respond(
                RESPONDER_ID, "no-es-un-numero", action("ACCEPT")),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsEntry("id",
                FriendshipRequestService.REQUEST_INVALID_MESSAGE);
        verify(requestRepository, never()).respondPending(any(), any(), any(), any());
    }

    @Test
    void aNonPositiveRequestIdIsAFieldErrorBeforeAnyQuery() {
        FieldValidationException exception = catchThrowableOfType(() -> service.respond(
                RESPONDER_ID, "0", action("ACCEPT")), FieldValidationException.class);

        assertThat(exception.getFields())
                .containsEntry("id", FriendshipRequestService.REQUEST_INVALID_MESSAGE);
        verify(requestRepository, never()).respondPending(any(), any(), any(), any());
    }

    @Test
    void zeroUpdatedRowsWithNoRowIsANeutral404() {
        when(requestRepository.respondPending(eq(REQUEST_ID), eq(RESPONDER_ID),
                eq(FriendshipRequestStatus.ACCEPTED), any(OffsetDateTime.class)))
                .thenReturn(0);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        catchThrowableOfType(
                () -> service.respond(RESPONDER_ID, String.valueOf(REQUEST_ID), action("ACCEPT")),
                FriendshipRequestNotFoundException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void zeroUpdatedRowsOnSomeoneElsesRequestIsA404WithoutRevealingIt() {
        FriendshipRequest foreign = pendingRequest();
        foreign.setRecipientId(99L);
        when(requestRepository.respondPending(eq(REQUEST_ID), eq(RESPONDER_ID),
                eq(FriendshipRequestStatus.ACCEPTED), any(OffsetDateTime.class)))
                .thenReturn(0);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(foreign));

        FriendshipRequestNotFoundException exception = catchThrowableOfType(
                () -> service.respond(RESPONDER_ID, String.valueOf(REQUEST_ID), action("ACCEPT")),
                FriendshipRequestNotFoundException.class);

        assertThat(exception.getMessage())
                .isEqualTo("No se encontró la solicitud de amistad");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void zeroUpdatedRowsOnAnAlreadyAcceptedRequestIsA409WithoutRewritingIt() {
        FriendshipRequest accepted = pendingRequest();
        accepted.setStatus(FriendshipRequestStatus.ACCEPTED);
        accepted.setRespondedAt(OffsetDateTime.now());
        when(requestRepository.respondPending(eq(REQUEST_ID), eq(RESPONDER_ID),
                eq(FriendshipRequestStatus.REJECTED), any(OffsetDateTime.class)))
                .thenReturn(0);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(accepted));

        FriendshipRequestAlreadyRespondedException exception = catchThrowableOfType(
                () -> service.respond(RESPONDER_ID, String.valueOf(REQUEST_ID), action("REJECT")),
                FriendshipRequestAlreadyRespondedException.class);

        assertThat(exception.getMessage()).isEqualTo("Esta solicitud de amistad ya fue respondida");
        assertThat(accepted.getStatus()).isEqualTo(FriendshipRequestStatus.ACCEPTED);
        verify(requestRepository, never()).save(any(FriendshipRequest.class));
    }

    @Test
    void zeroUpdatedRowsOnAnAlreadyRejectedRequestIsTheSame409() {
        FriendshipRequest rejected = pendingRequest();
        rejected.setStatus(FriendshipRequestStatus.REJECTED);
        rejected.setRespondedAt(OffsetDateTime.now());
        when(requestRepository.respondPending(eq(REQUEST_ID), eq(RESPONDER_ID),
                eq(FriendshipRequestStatus.ACCEPTED), any(OffsetDateTime.class)))
                .thenReturn(0);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(rejected));

        catchThrowableOfType(
                () -> service.respond(RESPONDER_ID, String.valueOf(REQUEST_ID), action("ACCEPT")),
                FriendshipRequestAlreadyRespondedException.class);

        verify(requestRepository, never()).save(any(FriendshipRequest.class));
    }
}
