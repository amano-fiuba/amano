package com.amano.backend.payment;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissionDeniedException;

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

    private final PaymentRepository paymentRepository;
    private final DebtRepository debtRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionChecker permissionChecker;
    private final Clock clock;

    public PaymentDecisionService(PaymentRepository paymentRepository, DebtRepository debtRepository,
            GroupMemberRepository groupMemberRepository, GroupPermissionChecker permissionChecker, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.debtRepository = debtRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.permissionChecker = permissionChecker;
        this.clock = clock;
    }

    private record Locked(Payment payment, Debt debt) {
    }

    @Transactional
    public PaymentConfirmationResponse confirm(Long callerId, Long groupId, Long paymentId) {
        Locked locked = lockAndAuthorize(callerId, groupId, paymentId, CONFIRM_NOT_ADMIN);
        locked.payment().confirmBy(callerId, OffsetDateTime.now(clock));
        locked.debt().confirmReportedPayment();
        return new PaymentConfirmationResponse(locked.payment().getId(), locked.payment().getStatus(),
                locked.debt().getStatus(), callerId);
    }

    @Transactional
    public PaymentRejectionResponse reject(Long callerId, Long groupId, Long paymentId) {
        Locked locked = lockAndAuthorize(callerId, groupId, paymentId, REJECT_NOT_ADMIN);
        locked.payment().rejectBy(callerId, OffsetDateTime.now(clock));
        locked.debt().rejectReportedPayment(locked.payment().getAmount());
        return new PaymentRejectionResponse(locked.payment().getId(), locked.payment().getStatus(),
                locked.debt().getStatus(), callerId);
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
        if (payment.isLegacy() || payment.getStatus() != PaymentStatus.REPORTED
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
