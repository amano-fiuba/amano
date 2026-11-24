package com.amano.backend.friendship;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.notification.NotificationEvent;
import com.amano.backend.notification.NotificationEvent.EmailPolicy;
import com.amano.backend.notification.NotificationEventPublisher;
import com.amano.backend.notification.NotificationType;
import com.amano.backend.notification.dto.NotificationTargetResponse;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Friendship requests (features 011 and 012, research.md D3/D5/D10). The sender
 * always comes from the session; the server validates the recipient, the pair
 * invariants and maps responses to the minimal DTO. Feature 012 adds the only
 * mutation of the lifecycle: the respondent answers a pending request they
 * received through a conditional UPDATE, so acceptance and rejection are the
 * same statement and concurrency keeps exactly one transition.
 */
@Service
public class FriendshipRequestService {

    public static final String RECIPIENT_REQUIRED_MESSAGE = "El destinatario es obligatorio";
    public static final String RECIPIENT_INVALID_MESSAGE = "El destinatario no es válido";
    public static final String SELF_REQUEST_MESSAGE =
            "No podés enviarte una solicitud de amistad a vos mismo";
    public static final String REQUEST_INVALID_MESSAGE = "La solicitud no es válida";

    private final FriendshipRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final NotificationEventPublisher notificationPublisher;

    /** Constructor kept for the existing unit tests (no notification publishing). */
    public FriendshipRequestService(FriendshipRequestRepository requestRepository,
            UserRepository userRepository) {
        this(requestRepository, userRepository, NotificationEventPublisher.noOp());
    }

    @Autowired
    public FriendshipRequestService(FriendshipRequestRepository requestRepository,
            UserRepository userRepository, NotificationEventPublisher notificationPublisher) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.notificationPublisher = notificationPublisher;
    }

    /** Sends a pending request (US1/US2) and returns the created row. */
    @Transactional
    public FriendshipRequestResponse send(Long senderId, Long recipientId) {
        if (recipientId == null) {
            throw new FieldValidationException(Map.of("recipientId", RECIPIENT_REQUIRED_MESSAGE));
        }
        if (recipientId <= 0) {
            throw new FieldValidationException(Map.of("recipientId", RECIPIENT_INVALID_MESSAGE));
        }
        if (recipientId.equals(senderId)) {
            throw new FieldValidationException(Map.of("recipientId", SELF_REQUEST_MESSAGE));
        }

        User recipient = userRepository.findById(recipientId)
                .filter(user -> user.getEmailVerifiedAt() != null)
                .orElseThrow(FriendshipRecipientNotFoundException::new);

        if (requestRepository.existsBetween(senderId, recipientId,
                FriendshipRequestStatus.ACCEPTED)) {
            throw new AlreadyFriendsException();
        }
        if (requestRepository.existsBetween(senderId, recipientId,
                FriendshipRequestStatus.PENDING)) {
            throw new PendingFriendshipRequestExistsException();
        }

        FriendshipRequest request = new FriendshipRequest();
        request.setSenderId(senderId);
        request.setRecipientId(recipientId);
        request.setStatus(FriendshipRequestStatus.PENDING);

        try {
            // Flush so the partial unique index answers a concurrent race here,
            // inside this transaction, instead of at commit time (FR-009).
            FriendshipRequest saved = requestRepository.saveAndFlush(request);
            notificationPublisher.publishSafely(new NotificationEvent(
                    recipientId, NotificationType.FRIEND_REQUEST, "Solicitud de amistad",
                    "Tenés una solicitud de amistad pendiente.",
                    new NotificationTargetResponse("FRIEND_REQUEST", null, null, null, saved.getId()),
                    EmailPolicy.SUBSCRIBABLE));
            return FriendshipRequestResponse.from(saved, recipient);
        }
        catch (DataIntegrityViolationException exception) {
            throw new PendingFriendshipRequestExistsException();
        }
    }

    /** Pending requests received by the session user, newest first (US3). */
    @Transactional(readOnly = true)
    public List<ReceivedFriendshipRequestResponse> listReceivedPending(Long recipientId) {
        List<FriendshipRequest> pending = requestRepository
                .findByRecipientIdAndStatusOrderByCreatedAtDescIdDesc(recipientId,
                        FriendshipRequestStatus.PENDING);
        if (pending.isEmpty()) {
            return List.of();
        }

        Set<Long> senderIds = pending.stream()
                .map(FriendshipRequest::getSenderId)
                .collect(Collectors.toSet());
        Map<Long, User> senders = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> sendersWithAvatar = userRepository.findIdsWithAvatar(senderIds);

        return pending.stream()
                .map(request -> {
                    User sender = senders.get(request.getSenderId());
                    return sender == null ? null
                            : ReceivedFriendshipRequestResponse.from(request, sender,
                                    sendersWithAvatar.contains(sender.getId()));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Answers a pending request received by the session user (feature 012,
     * FR-007..FR-011). Validations happen before any query; the transition is a
     * single conditional UPDATE and the re-read only disambiguates 404 from 409
     * when it changed nothing (research.md D5/D6).
     */
    @Transactional
    public AnsweredFriendshipRequestResponse respond(Long responderId, String requestIdRaw,
            RespondFriendshipRequestRequest body) {
        long requestId = parseRequestId(requestIdRaw);
        FriendshipRequestAction action = body == null || body.action() == null
                ? null
                : FriendshipRequestAction.fromValue(body.action());
        if (action == null) {
            throw new FieldValidationException(
                    Map.of("action", FriendshipRequestAction.INVALID_ACTION_MESSAGE));
        }

        int updated = requestRepository.respondPending(requestId, responderId,
                action.targetStatus(), OffsetDateTime.now());
        if (updated > 0) {
            FriendshipRequest request = requestRepository.findById(requestId)
                    .orElseThrow(FriendshipRequestNotFoundException::new);
            User sender = userRepository.findById(request.getSenderId())
                    .orElseThrow(FriendshipRequestNotFoundException::new);
            notificationPublisher.publishSafely(new NotificationEvent(
                    request.getSenderId(), NotificationType.FRIEND_ACTIVITY,
                    "Respuesta a tu solicitud de amistad", "Tu solicitud de amistad fue respondida.",
                    new NotificationTargetResponse("FRIEND_REQUEST", null, null, null, request.getId()),
                    EmailPolicy.SUBSCRIBABLE));
            return AnsweredFriendshipRequestResponse.from(request, sender);
        }

        // The conditional UPDATE affected nothing: either the row is missing or
        // directed to someone else (404 neutral), or it was already answered
        // (409 without revealing the previous outcome).
        FriendshipRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || !responderId.equals(request.getRecipientId())) {
            throw new FriendshipRequestNotFoundException();
        }
        throw new FriendshipRequestAlreadyRespondedException();
    }

    /** Non-numeric or non-positive path id is a 400 before any query (FR-010). */
    private static long parseRequestId(String requestIdRaw) {
        if (requestIdRaw == null || requestIdRaw.isBlank()) {
            throw new FieldValidationException(Map.of("id", REQUEST_INVALID_MESSAGE));
        }
        try {
            long requestId = Long.parseLong(requestIdRaw);
            if (requestId <= 0) {
                throw new FieldValidationException(Map.of("id", REQUEST_INVALID_MESSAGE));
            }
            return requestId;
        }
        catch (NumberFormatException exception) {
            throw new FieldValidationException(Map.of("id", REQUEST_INVALID_MESSAGE));
        }
    }
}
