package com.amano.backend.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseAmountNormalizer;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissionDeniedException;
import com.amano.backend.groups.GroupPermissions;
import com.amano.backend.groups.GroupPermissionsRepository;
import com.amano.backend.groups.PaymentActivityDetails;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Registration of a full debt payment (022 US6, plan.md D6). Only the whole
 * outstanding balance is accepted; the caller must be an active member allowed
 * to register the payment, and payer, receiver and currency come from the debt.
 * The debt row is taken with {@code PESSIMISTIC_WRITE}, so two concurrent
 * payments with different keys are serialized and only the first one applies.
 *
 * <p>Without the group's "Pagos requiere validación" flag the payment is
 * {@code CONFIRMED} and the debt becomes {@code PAID}; with it the payment is
 * {@code REPORTED} and the debt becomes {@code PAYMENT_REPORTED} until an
 * administrator decides (US5, out of this phase).
 */
@Service
public class PaymentValidationService {

    static final String NOT_ALLOWED =
            "Solo quien debe, quien cobra o un administrador pueden registrar este pago";
    static final String PARTIAL_PAYMENT = "El monto del pago debe ser el total de la deuda pendiente";
    static final String EXCESSIVE_PAYMENT = "El monto del pago excede la deuda pendiente";
    static final String KEY_REQUIRED = "Indicá la clave de idempotencia";
    static final String KEY_FORMAT = "La clave de idempotencia debe ser un UUID válido";
    static final String UNKNOWN_PROPERTY = "No se permite informar este campo";

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final GroupPermissionChecker permissionChecker;
    private final UserRepository userRepository;
    private final ActivityEventService activityEventService;
    private final Clock clock;

    public PaymentValidationService(DebtRepository debtRepository, PaymentRepository paymentRepository,
            GroupMemberRepository groupMemberRepository, GroupPermissionsRepository groupPermissionsRepository,
            GroupPermissionChecker permissionChecker, UserRepository userRepository,
            ActivityEventService activityEventService, Clock clock) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.permissionChecker = permissionChecker;
        this.userRepository = userRepository;
        this.activityEventService = activityEventService;
        this.clock = clock;
    }

    /** Result of a registration: the representation plus whether a payment was inserted. */
    public record Registration(PaymentRegistrationResponse payment, boolean created) {
    }

    private record NormalizedPayment(BigDecimal amount, UUID idempotencyKey) {
    }

    @Transactional
    public Registration register(Long callerId, Long groupId, Long debtId, RegisterDebtPaymentRequest request) {
        // The first read takes the lock (plan.md D6): concurrent payments of the
        // same debt wait here and the loser sees the winner's committed state.
        Debt debt = debtRepository.findByIdForUpdate(debtId).orElseThrow(DebtNotFoundException::new);
        if (!debt.getGroupId().equals(groupId)) {
            throw new DebtNotFoundException();
        }
        GroupMember caller = activeMembership(callerId, groupId);
        if (!permissionChecker.canRegisterPayment(caller.getRole(), callerId, debt.getDebtorUserId(),
                debt.getCreditorUserId())) {
            throw new GroupPermissionDeniedException(NOT_ALLOWED);
        }
        NormalizedPayment normalized = normalize(request);

        Optional<Payment> existing =
                paymentRepository.findByDebtIdAndIdempotencyKey(debtId, normalized.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), normalized, debt);
        }
        // Reusing a key that this member already used on another debt keeps the
        // unique (registered_by, key) rule of 018 and answers 422.
        if (paymentRepository.findByRegisteredByUserIdAndIdempotencyKey(callerId, normalized.idempotencyKey())
                .isPresent()) {
            throw new IdempotencyConflictException();
        }

        if (!debt.getStatus().isPayable()) {
            throw new DebtAlreadyHasPaymentException();
        }
        requireExactAmount(normalized.amount(), debt.getOutstanding());

        boolean requiresValidation = groupPermissionsRepository.findByGroupId(groupId)
                .map(GroupPermissions::isPaymentsRequireValidation)
                .orElse(false);
        String debtStatusBefore = debt.getStatus().name();
        debt.applyFullPayment(requiresValidation);
        PaymentStatus status = requiresValidation ? PaymentStatus.REPORTED : PaymentStatus.CONFIRMED;
        Payment payment = paymentRepository.save(Payment.of(debt, normalized.amount(), LocalDate.now(clock),
                callerId, nameOf(debt.getCreditorUserId()), normalized.idempotencyKey(), false, status));

        activityEventService.recordPaymentRegistered(debt.getGroupId(), callerId, new PaymentActivityDetails(
                payment.getId(), payment.getAmount(), payment.getCurrency().name(), payment.getPayerName(),
                payment.getReceiverName(), debt.getExpenseId(), debt.getId(),
                debtStatusBefore, debt.getStatus().name()));

        return new Registration(toResponse(payment, debt), true);
    }

    /** Same key on the same debt returns the stored payment instead of a duplicate. */
    private Registration replay(Payment existing, NormalizedPayment normalized, Debt debt) {
        if (existing.getAmount().compareTo(normalized.amount()) != 0) {
            throw new IdempotencyConflictException();
        }
        return new Registration(toResponse(existing, debt), false);
    }

    private static void requireExactAmount(BigDecimal amount, BigDecimal outstanding) {
        if (amount.compareTo(outstanding) < 0) {
            throw new FieldValidationException(Map.of("amount", PARTIAL_PAYMENT));
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new FieldValidationException(Map.of("amount", EXCESSIVE_PAYMENT));
        }
    }

    private GroupMember activeMembership(Long userId, Long groupId) {
        return groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId)
                .filter(membership -> membership.getLeftAt() == null)
                .orElseThrow(DebtNotFoundException::new);
    }

    private NormalizedPayment normalize(RegisterDebtPaymentRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        String amountError = ExpenseAmountNormalizer.validationError(request.getAmount());
        if (amountError != null) {
            fieldErrors.put("amount", amountError);
        }
        UUID key = parseKey(request.getIdempotencyKey(), fieldErrors);
        for (String unknown : request.getUnknownProperties().keySet()) {
            fieldErrors.put(unknown, UNKNOWN_PROPERTY);
        }
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }
        return new NormalizedPayment(ExpenseAmountNormalizer.normalize(request.getAmount()), key);
    }

    private static UUID parseKey(String raw, Map<String, String> fieldErrors) {
        if (raw == null || raw.isBlank()) {
            fieldErrors.put("idempotencyKey", KEY_REQUIRED);
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        }
        catch (IllegalArgumentException exception) {
            fieldErrors.put("idempotencyKey", KEY_FORMAT);
            return null;
        }
    }

    private PaymentRegistrationResponse toResponse(Payment payment, Debt debt) {
        return new PaymentRegistrationResponse(payment.getId(), payment.getStatus(), debt.getStatus(),
                payment.getDecidedByUserId(), payment.getDecidedAt());
    }

    /** Current visible name of a user (014 D4), falling back to the email as 017 does. */
    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(PaymentValidationService::displayOrEmail).orElse(null);
    }

    private static String displayOrEmail(User user) {
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }
}
