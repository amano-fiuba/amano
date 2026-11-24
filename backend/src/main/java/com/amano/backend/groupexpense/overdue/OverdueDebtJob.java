package com.amano.backend.groupexpense.overdue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.notification.NotificationEvent;
import com.amano.backend.notification.NotificationEvent.EmailPolicy;
import com.amano.backend.notification.NotificationEventPublisher;
import com.amano.backend.notification.NotificationType;
import com.amano.backend.notification.dto.NotificationTargetResponse;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupRepository;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Daily overdue-debt job (022 D7, T063, T049). For every group with a non-null
 * {@code debtDueDays}, a debt still in {@code PENDING} whose origin expense is
 * older than {@code today - debtDueDays} becomes {@code OVERDUE} and records a
 * {@code DEBT_OVERDUE} event in the group activity history (Q7). The boundary
 * is strict: the debt does not expire on the deadline day itself, only the day
 * after. A null deadline means the group never expires debts.
 *
 * <p>Each debt is transitioned with a conditional {@code status = PENDING}
 * update, so running the job twice (or on several instances at once) is
 * idempotent and records the history event at most once. It never touches
 * {@code PARTIALLY_PAID}, {@code PAYMENT_REPORTED}, {@code PAID} or
 * {@code VERIFIED}. The clock is injectable for deterministic tests and the
 * cadence comes from {@code overdue.cron}.
 */
@Component
public class OverdueDebtJob {

    public static final String ZONE = "America/Argentina/Buenos_Aires";

    private final GroupRepository groupRepository;
    private final DebtRepository debtRepository;
    private final UserRepository userRepository;
    private final ActivityEventService activityEventService;
    private final Clock clock;
    private final NotificationEventPublisher notificationPublisher;

    /** Constructor kept for the existing integration tests (no notification publishing). */
    public OverdueDebtJob(GroupRepository groupRepository, DebtRepository debtRepository,
            UserRepository userRepository, ActivityEventService activityEventService, Clock clock) {
        this(groupRepository, debtRepository, userRepository, activityEventService, clock,
                NotificationEventPublisher.noOp());
    }

    @Autowired
    public OverdueDebtJob(GroupRepository groupRepository, DebtRepository debtRepository,
            UserRepository userRepository, ActivityEventService activityEventService, Clock clock,
            NotificationEventPublisher notificationPublisher) {
        this.groupRepository = groupRepository;
        this.debtRepository = debtRepository;
        this.userRepository = userRepository;
        this.activityEventService = activityEventService;
        this.clock = clock;
        this.notificationPublisher = notificationPublisher;
    }

    /** Marks the due PENDING debts as OVERDUE; a second run changes nothing. */
    @Scheduled(cron = "${overdue.cron:0 30 0 * * *}", zone = ZONE)
    @Transactional
    public void markOverdueDebts() {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Group group : groupRepository.findByDebtDueDaysIsNotNull()) {
            List<Debt> dueDebts = debtRepository.findPendingDueBefore(group.getId(), today);
            if (dueDebts.isEmpty()) {
                continue;
            }
            Map<Long, String> creditorNames = creditorNamesOf(dueDebts);
            for (Debt debt : dueDebts) {
                if (debtRepository.markDebtOverdueIfPending(debt.getId(), now) > 0) {
                    activityEventService.recordDebtOverdue(
                            group.getId(),
                            debt.getId(),
                            debt.getExpenseId(),
                            debt.getAmount(),
                            debt.getCurrency().name(),
                            debt.getDebtorName(),
                            creditorNames.get(debt.getCreditorUserId()));
                    if (debt.getDebtorUserId() != null) {
                        notificationPublisher.publishSafely(new NotificationEvent(
                                debt.getDebtorUserId(), NotificationType.DEBT_OVERDUE,
                                "Deuda vencida", "Tenés una deuda vencida en el grupo.",
                                new NotificationTargetResponse("DEBT", group.getId(), debt.getId(),
                                        debt.getExpenseId(), null), EmailPolicy.ALWAYS));
                    }
                }
            }
        }
    }

    private Map<Long, String> creditorNamesOf(List<Debt> debts) {
        Set<Long> creditorIds = debts.stream().map(Debt::getCreditorUserId).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(creditorIds)) {
            String display = ActivityEventService.displayName(user);
            names.put(user.getId(), display == null ? user.getEmail() : display);
        }
        return names;
    }
}
