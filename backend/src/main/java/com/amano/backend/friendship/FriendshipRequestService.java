package com.amano.backend.friendship;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Friendship requests (feature 011, research.md D3/D5/D10). The sender always
 * comes from the session; the server validates the recipient, the pair invariants
 * and maps responses to the minimal DTO. Accepting/rejecting is out of scope: only
 * PENDING rows are created and listed.
 */
@Service
public class FriendshipRequestService {

    public static final String RECIPIENT_REQUIRED_MESSAGE = "El destinatario es obligatorio";
    public static final String RECIPIENT_INVALID_MESSAGE = "El destinatario no es válido";
    public static final String SELF_REQUEST_MESSAGE =
            "No podés enviarte una solicitud de amistad a vos mismo";

    private final FriendshipRequestRepository requestRepository;
    private final UserRepository userRepository;

    public FriendshipRequestService(FriendshipRequestRepository requestRepository,
            UserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
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

        return pending.stream()
                .map(request -> {
                    User sender = senders.get(request.getSenderId());
                    return sender == null ? null
                            : ReceivedFriendshipRequestResponse.from(request, sender);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
