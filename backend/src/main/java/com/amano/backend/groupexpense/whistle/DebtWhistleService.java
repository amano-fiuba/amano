package com.amano.backend.groupexpense.whistle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseRepository;
import com.amano.backend.groupexpense.ParticipantType;
import com.amano.backend.groupexpense.reminder.ReminderMailService;
import com.amano.backend.groupexpense.reminder.ReminderMessageBuilder;
import com.amano.backend.groupexpense.reminder.ReminderRecipients;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupNotFoundException;
import com.amano.backend.groups.GroupRepository;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Whistle (030): the creditor of an open group debt sends the debtor an
 * immediate mail. Three steps, the claim pattern of 025 (research.md D3):
 *
 * <ol>
 * <li>Claim transaction: lock the creditor's membership row, validate the
 * rules in the order of research.md D4, apply the 24 h limit of the pair and
 * insert a {@code SENDING} attempt.</li>
 * <li>Send the mail outside any transaction.</li>
 * <li>Mark {@code SENT} and record {@code DEBT_WHISTLED} in one transaction,
 * or mark {@code FAILED} and answer 503 (the attempt no longer blocks).</li>
 * </ol>
 *
 * Whistling never touches the debt (FR-006).
 */
@Service
public class DebtWhistleService {

    static final String NOT_MEMBER = "No existe el grupo solicitado o no sos miembro";
    static final String SETTLED = "La deuda ya no tiene saldo pendiente";
    static final String PAYMENT_WAITING = "La deuda tiene un pago esperando validación";
    static final Duration WINDOW = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(DebtWhistleService.class);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final DebtRepository debtRepository;
    private final GroupExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final DebtWhistleRepository whistleRepository;
    private final ReminderRecipients recipients;
    private final ReminderMessageBuilder messageBuilder;
    private final ReminderMailService mailService;
    private final ActivityEventService activityEventService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public DebtWhistleService(GroupRepository groupRepository, GroupMemberRepository groupMemberRepository,
            DebtRepository debtRepository, GroupExpenseRepository expenseRepository, UserRepository userRepository,
            DebtWhistleRepository whistleRepository, ReminderRecipients recipients,
            ReminderMessageBuilder messageBuilder, ReminderMailService mailService,
            ActivityEventService activityEventService, Clock clock, PlatformTransactionManager transactionManager) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.debtRepository = debtRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.whistleRepository = whistleRepository;
        this.recipients = recipients;
        this.messageBuilder = messageBuilder;
        this.mailService = mailService;
        this.activityEventService = activityEventService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Whistles {@code debtId} of {@code groupId} on behalf of {@code callerId}. */
    public DebtWhistleResponse whistle(Long callerId, Long groupId, Long debtId) {
        Claim claim = transactionTemplate.execute(status -> claim(callerId, groupId, debtId));
        Long whistleId = claim.whistle().getId();

        String email = recipients.emailOf(claim.debt()).orElse(null);
        if (email == null) {
            log.warn("Whistle {} of group {} has no recipient email; marking FAILED", whistleId, groupId);
            fail(whistleId);
        }
        try {
            mailService.send(email, claim.mail());
        }
        catch (Exception exception) {
            // Only the exception type: SMTP messages may echo the recipient address (NFR-002).
            log.warn("Whistle {} of group {} could not be sent: {}", whistleId, groupId,
                    exception.getClass().getSimpleName());
            fail(whistleId);
        }

        OffsetDateTime sentAt = OffsetDateTime.now(clock);
        transactionTemplate.executeWithoutResult(status -> {
            if (whistleRepository.markSent(whistleId, sentAt) == 0) {
                return;
            }
            Debt debt = claim.debt();
            activityEventService.recordDebtWhistled(groupId, callerId, whistleId, debt.getId(), debt.getExpenseId(),
                    claim.outstanding(), debt.getCurrency().name(), debt.getDebtorName(), claim.creditorName(),
                    claim.concept());
        });
        return new DebtWhistleResponse(whistleId, debtId, claim.debt().getDebtorName(), sentAt,
                claim.whistle().getCreatedAt().plus(WINDOW));
    }

    private Claim claim(Long callerId, Long groupId, Long debtId) {
        Group group = groupRepository.findById(groupId).orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
        GroupMember caller = groupMemberRepository.findForUpdate(groupId, callerId)
                .orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
        if (caller.getLeftAt() != null) {
            throw new GroupNotFoundException(NOT_MEMBER);
        }
        Debt debt = debtRepository.findById(debtId)
                .filter(found -> groupId.equals(found.getGroupId()))
                .orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
        if (!callerId.equals(debt.getCreditorUserId())) {
            throw new WhistleNotCreditorException();
        }
        if (debt.getStatus() == DebtStatus.PAYMENT_REPORTED) {
            throw new DebtNotWhistleableException(PAYMENT_WAITING, DebtNotWhistleableException.NOT_WHISTLEABLE);
        }
        if (!debt.getStatus().isPayable()) {
            throw new DebtNotWhistleableException(SETTLED, DebtNotWhistleableException.NOT_WHISTLEABLE);
        }
        if (debt.getDebtorType() == ParticipantType.SIMULATED) {
            throw new SimulatedDebtorWhistleException();
        }
        boolean debtorActive = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, debt.getDebtorUserId())
                .filter(member -> member.getLeftAt() == null)
                .isPresent();
        if (!debtorActive) {
            throw new DebtNotWhistleableException(debt.getDebtorName() + " ya no forma parte del grupo",
                    DebtNotWhistleableException.DEBTOR_NOT_MEMBER);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DebtWhistle> blocking = whistleRepository.findBlocking(groupId, callerId, debt.getDebtorUserId(),
                now.minus(WINDOW));
        if (!blocking.isEmpty()) {
            OffsetDateTime newest = blocking.stream().map(DebtWhistle::getCreatedAt)
                    .max(OffsetDateTime::compareTo).orElseThrow();
            throw new WhistleRateLimitedException(debt.getDebtorName(), secondsUntil(now, newest.plus(WINDOW)));
        }

        GroupExpense expense = expenseRepository.findById(debt.getExpenseId()).orElse(null);
        String concept = expense == null ? null : expense.getDescription();
        String creditorName = creditorName(callerId);
        BigDecimal outstanding = debt.getOutstanding().setScale(2, RoundingMode.HALF_UP);
        ReminderMessageBuilder.Mail mail = messageBuilder.debtWhistle(group.getName(), creditorName,
                debt.getDebtorName(), concept, outstanding, debt.getCurrency(),
                expense == null ? null : expense.getDueDate(), expense);

        DebtWhistle whistle = whistleRepository.save(
                DebtWhistle.sending(groupId, debt.getId(), callerId, debt.getDebtorUserId(), now));
        return new Claim(whistle, debt, outstanding, creditorName, concept, mail);
    }

    private void fail(Long whistleId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        transactionTemplate.executeWithoutResult(status -> whistleRepository.markFailed(whistleId, now));
        throw new WhistleDeliveryException();
    }

    private String creditorName(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return "Un integrante del grupo";
        }
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }

    /** Whole seconds from {@code now} to {@code until}, rounded up (never 0 while blocked). */
    static long secondsUntil(OffsetDateTime now, OffsetDateTime until) {
        Duration remaining = Duration.between(now, until);
        long seconds = remaining.getSeconds();
        return remaining.getNano() > 0 ? seconds + 1 : Math.max(seconds, 1);
    }

    private record Claim(DebtWhistle whistle, Debt debt, BigDecimal outstanding, String creditorName,
            String concept, ReminderMessageBuilder.Mail mail) {
    }
}
