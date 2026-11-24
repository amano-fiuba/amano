package com.amano.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.notification.NotificationEvent.EmailPolicy;
import com.amano.backend.notification.dto.NotificationTargetResponse;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Turns a committed business event into an in-app notice and/or an email
 * (feature 035, plan D4/D5). The two channels are attempted independently: a
 * failure of either is logged and swallowed, so it can never roll back or fail
 * the operation that published the event.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationPreferenceService preferenceService;
    private final NotificationRepository repository;
    private final NotificationMailService mailService;
    private final UserRepository userRepository;

    public NotificationDeliveryService(NotificationPreferenceService preferenceService,
            NotificationRepository repository, NotificationMailService mailService,
            UserRepository userRepository) {
        this.preferenceService = preferenceService;
        this.repository = repository;
        this.mailService = mailService;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(NotificationEvent event) {
        UserNotificationPreferences preferences;
        try {
            preferences = preferenceService.getOrCreate(event.userId());
        }
        catch (RuntimeException exception) {
            log.warn("Could not read notification preferences for user {}: {}",
                    event.userId(), exception.getMessage());
            return;
        }
        if (!isTypeEnabled(preferences, event.type())) {
            return;
        }

        if (preferences.isInApp()) {
            try {
                repository.save(build(event));
            }
            catch (RuntimeException exception) {
                log.warn("Could not store notification of type {} for user {}: {}",
                        event.type(), event.userId(), exception.getMessage());
            }
        }

        if (shouldSendEmail(preferences, event)) {
            try {
                userRepository.findById(event.userId())
                        .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                        .ifPresentOrElse(
                                user -> mailService.send(user.getEmail(), event.type(), event.title(),
                                        event.message()),
                                () -> log.warn("No email address for user {}; notification email skipped",
                                        event.userId()));
            }
            catch (RuntimeException exception) {
                log.warn("Could not dispatch notification email of type {} for user {}: {}",
                        event.type(), event.userId(), exception.getMessage());
            }
        }
    }

    private static boolean isTypeEnabled(UserNotificationPreferences preferences, NotificationType type) {
        return switch (type) {
            case REMINDER, DEBT_WHISTLE -> preferences.isRemindersWhistles();
            case DEBT_OVERDUE, PAYER_DUE_NOTICE -> preferences.isDebtDueCollections();
            case GROUP_EXPENSE, PAYMENT_RECORDED -> preferences.isGroupActivity();
            case FRIEND_REQUEST, FRIEND_ACTIVITY -> preferences.isFriendActivity();
        };
    }

    private static boolean shouldSendEmail(UserNotificationPreferences preferences, NotificationEvent event) {
        EmailPolicy policy = event.emailPolicy() == null ? EmailPolicy.SUBSCRIBABLE : event.emailPolicy();
        return switch (policy) {
            case NONE -> false;
            case ALWAYS -> true;
            case SUBSCRIBABLE -> preferences.isEmail();
        };
    }

    private static Notification build(NotificationEvent event) {
        NotificationTargetResponse target = event.target();
        Notification notice = new Notification();
        notice.setUserId(event.userId());
        notice.setType(event.type());
        notice.setTitle(event.title());
        notice.setMessage(event.message());
        NotificationTargetKind kind = targetKindOf(target);
        notice.setTargetKind(kind);
        if (target != null && kind != NotificationTargetKind.NONE) {
            notice.setGroupId(target.groupId());
            notice.setDebtId(target.debtId());
            notice.setExpenseId(target.expenseId());
            notice.setFriendRequestId(target.friendRequestId());
        }
        return notice;
    }

    private static NotificationTargetKind targetKindOf(NotificationTargetResponse target) {
        if (target == null || target.kind() == null) {
            return NotificationTargetKind.NONE;
        }
        return switch (target.kind()) {
            case "GROUP" -> target.groupId() != null ? NotificationTargetKind.GROUP : NotificationTargetKind.NONE;
            case "DEBT" -> target.debtId() != null ? NotificationTargetKind.DEBT : NotificationTargetKind.NONE;
            case "FRIEND_REQUEST" -> NotificationTargetKind.FRIEND_REQUEST;
            default -> NotificationTargetKind.NONE;
        };
    }
}
