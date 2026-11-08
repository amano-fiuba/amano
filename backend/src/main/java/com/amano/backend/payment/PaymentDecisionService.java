package com.amano.backend.payment;


import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissionDeniedException;
import com.amano.backend.groups.PaymentActivityDetails;

/**
 * Administrator decision over a reported payment (022 US5, plan.md D6). Confirm
 * moves the payment {@code REPORTED → CONFIRMED} and the debt
 * {@code PAYMENT_REPORTED → VERIFIED}; reject moves the payment
 * {@code REPORTED → REJECTED} and returns the debt to {@code PENDING} (or to
 * the legacy {@code PARTIALLY_PAID} state when an earlier 018 payment existed).
 *
 * <p>The debt row is locked first with {@code PESSIMISTIC_WRITE}, in the same
 * order as registration, and then the payment row is locked. Two concurrent
 * decisions are therefore serialized: the loser sees the committed decision and
 * answers 409 without overwriting it. The decision instant comes from the
 * injectable {@link Clock}.
 */
@Service
public class PaymentDecisionService {

    static final String CONFIRM_NOT_ADMIN = "Solo los administradores pueden confirmar pagos";
    static final String REJECT_NOT_ADMIN = "Solo los administradores pueden rechazar pagos";
    static final String LIST_NOT_ADMIN = "Solo los administradores pueden ver los pagos por validar";

    private final PaymentRepository paymentRepository;
    private final DebtRepository debtRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionChecker permissionChecker;
    private final ActivityEventService activityEventService;
    private final Clock clock;

    public PaymentDecisionService(PaymentRepository paymentRepository, DebtRepository debtRepository,
            GroupMemberRepository groupMemberRepository, GroupPermissionChecker permissionChecker,
            ActivityEventService activityEventService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.debtRepository = debtRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.permissionChecker = permissionChecker;
        this.activityEventService = activityEventService;
        this.clock = clock;
    }

    private record Locked(Payment payment, Debt debt) {
    }

    /**
     * 022 T075: reported payments of the group awaiting a decision, oldest
     * first. Read-only; non-members get the neutral 404 and non-admin members a 403.
     */
    @Transactional(readOnly = true)
    public List<PendingPaymentDetail> pendingValidation(Long callerId, Long groupId) {
        GroupMember caller = activeMembership(callerId, groupId);
        if (!permissionChecker.canDecidePayments(caller.getRole())) {
            throw new GroupPermissionDeniedException(LIST_NOT_ADMIN);
        }
        List<Payment> payments = paymentRepository
                .findByGroupIdAndStatusOrderByCreatedAtAsc(groupId, PaymentStatus.REPORTED);
        Map<Long, Debt> debts = debtRepository
                .findAllById(payments.stream().map(Payment::getDebtId).toList()).stream()
                .collect(Collectors.toMap(Debt::getId, Function.identity()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        return payments.stream()
                .map(payment -> new PendingPaymentDetail(payment.getId(), payment.getDebtId(),
                        debts.containsKey(payment.getDebtId()) ? debts.get(payment.getDebtId()).getExpenseId() : null,
                        payment.getPayerUserId(), payment.getPayerName(), payment.getReceiverName(),
                        payment.getAmount().setScale(2, RoundingMode.HALF_UP), payment.getCurrency().name(),
                        payment.getCreatedAt(),
                        (int) Math.max(0, ChronoUnit.DAYS.between(payment.getCreatedAt(), now))))
                .toList();
    }

    @Transactional
    public PaymentConfirmationResponse confirm(Long callerId, Long groupId, Long paymentId) {
        Locked locked = lockAndAuthorize(callerId, groupId, paymentId, CONFIRM_NOT_ADMIN);
        String debtStatusBefore = locked.debt().getStatus().name();
        locked.payment().confirmBy(callerId, OffsetDateTime.now(clock));
        locked.debt().confirmReportedPayment(locked.payment().getAmount());
        activityEventService.recordPaymentConfirmed(groupId, callerId, detailsOf(locked, debtStatusBefore));
        return new PaymentConfirmationResponse(locked.payment().getId(), locked.payment().getStatus(),
                locked.debt().getStatus(), callerId);
    }

    @Transactional
    public PaymentRejectionResponse reject(Long callerId, Long groupId, Long paymentId) {
        Locked locked = lockAndAuthorize(callerId, groupId, paymentId, REJECT_NOT_ADMIN);
        String debtStatusBefore = locked.debt().getStatus().name();
        locked.payment().rejectBy(callerId, OffsetDateTime.now(clock));
        locked.debt().rejectReportedPayment();
        activityEventService.recordPaymentRejected(groupId, callerId, detailsOf(locked, debtStatusBefore));
        return new PaymentRejectionResponse(locked.payment().getId(), locked.payment().getStatus(),
                locked.debt().getStatus(), callerId);
    }

    private static PaymentActivityDetails detailsOf(Locked locked, String debtStatusBefore) {
        Payment payment = locked.payment();
        Debt debt = locked.debt();
        return new PaymentActivityDetails(payment.getId(), payment.getAmount(), payment.getCurrency().name(),
                payment.getPayerName(), payment.getReceiverName(), debt.getExpenseId(), debt.getId(),
                debtStatusBefore, debt.getStatus().name());
    }

    /**
     * Locks the debt first and then the payment, hides every payment outside
     * the group behind the same 404 and only then distinguishes a non-admin
     * member (403) from a decidable payment (409 when it is not REPORTED).
     */
    private Locked lockAndAuthorize(Long callerId, Long groupId, Long paymentId, String notAdminMessage) {
        Long debtId = paymentRepository.findDebtIdById(paymentId).orElseThrow(PaymentNotFoundException::new);
        Debt debt = debtRepository.findByIdForUpdate(debtId).orElseThrow(PaymentNotFoundException::new);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(PaymentNotFoundException::new);

        if (!groupId.equals(debt.getGroupId()) || !groupId.equals(payment.getGroupId())) {
            throw new PaymentNotFoundException();
        }
        GroupMember caller = activeMembership(callerId, groupId);
        if (!permissionChecker.canDecidePayments(caller.getRole())) {
            throw new GroupPermissionDeniedException(notAdminMessage);
        }
        if (payment.getStatus() != PaymentStatus.REPORTED
                || debt.getStatus() != DebtStatus.PAYMENT_REPORTED) {
            throw new PaymentNotDecidableException();
        }
        return new Locked(payment, debt);
    }

    private GroupMember activeMembership(Long userId, Long groupId) {
        return groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId)
                .filter(membership -> membership.getLeftAt() == null)
                .orElseThrow(PaymentNotFoundException::new);
    }
}
