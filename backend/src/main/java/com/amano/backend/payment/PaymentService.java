package com.amano.backend.payment;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseAmountNormalizer;
import com.amano.backend.expense.ExpenseDateValidator;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseRepository;
import com.amano.backend.groupexpense.GroupExpenseResponse;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.GroupPermissions;
import com.amano.backend.groups.GroupPermissionsRepository;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissionDeniedException;
import com.amano.backend.groups.PaymentActivityDetails;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Registration of a payment of a group debt (018 US1, plan.md §Flujo del
 * backend), aligned with the 022 rules (partial payments, D3/D6). Payer,
 * receiver, group and currency are copied from the debt (D3). The debt is read
 * once, with a pessimistic lock, before any other check (D5). Without the
 * group's "Pagos requiere validación" the payment is CONFIRMED and goes through
 * {@link Debt#applyPayment}; with it the payment is REPORTED and the debt only
 * becomes PAYMENT_REPORTED ({@link Debt#reportPayment}). The payment, the debt
 * update and the history event are written in ONE transaction (FR-010).
 */
@Service
public class PaymentService {

    static final String NOT_ALLOWED =
            "Solo quien debe, quien cobra o un administrador pueden registrar este pago";
    static final String DEBT_REQUIRED = "Indicá la deuda a pagar";
    static final String EXCEEDS_OUTSTANDING = "El pago no puede superar el saldo de $ %s";
    static final String BEFORE_EXPENSE = "La fecha del pago no puede ser anterior a la del gasto";
    static final String DATE_FORMAT = "La fecha debe tener el formato aaaa-mm-dd";
    static final String UNKNOWN_PROPERTY = "No se permite informar este campo";

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final GroupExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionChecker permissionChecker;
    private final UserRepository userRepository;
    private final ActivityEventService activityEventService;
    private final GroupPermissionsRepository groupPermissionsRepository;

    public PaymentService(DebtRepository debtRepository, PaymentRepository paymentRepository,
            GroupExpenseRepository expenseRepository, GroupMemberRepository groupMemberRepository,
            GroupPermissionChecker permissionChecker, UserRepository userRepository,
            ActivityEventService activityEventService, GroupPermissionsRepository groupPermissionsRepository) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.permissionChecker = permissionChecker;
        this.userRepository = userRepository;
        this.activityEventService = activityEventService;
        this.groupPermissionsRepository = groupPermissionsRepository;
    }

    /** Result of a registration: the representation plus whether a payment was inserted. */
    public record Registration(PaymentResponse payment, boolean created) {
    }

    private record NormalizedPayment(Long debtId, BigDecimal amount, LocalDate date) {
    }

    /** Steps 1–11 of plan.md §Flujo del backend, in that order. */
    @Transactional
    public Registration register(Long callerId, UUID idempotencyKey, RegisterPaymentRequest request) {
        if (request.getDebtId() == null) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            fieldErrors.put("debtId", DEBT_REQUIRED);
            validateInput(request, null, fieldErrors);
            throw new FieldValidationException(fieldErrors);
        }
        // The FIRST read takes the lock: a managed Debt read earlier without it
        // would be returned stale by the locking query (plan.md §Flujo, paso 1).
        Debt debt = debtRepository.findByIdForUpdate(request.getDebtId()).orElseThrow(DebtNotFoundException::new);
        // 027 US4: settling a direct debt (expense with a friend) is out of scope.
        if (debt.getGroupId() == null) {
            throw new DebtNotFoundException();
        }
        GroupMember caller = activeMembership(callerId, debt.getGroupId());
        if (!permissionChecker.canRegisterPayment(caller.getRole(), callerId, debt.getDebtorUserId(),
                debt.getCreditorUserId())) {
            throw new GroupPermissionDeniedException(NOT_ALLOWED);
        }
        NormalizedPayment normalized = normalize(request, expenseDateOf(debt));

        Optional<Payment> existing =
                paymentRepository.findByRegisteredByUserIdAndIdempotencyKey(callerId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), normalized, caller);
        }

        if (debt.getStatus().isSettled()) {
            throw new DebtAlreadyPaidException();
        }
        if (debt.getStatus() == DebtStatus.PAYMENT_REPORTED) {
            // 022 D6: only one payment waits for validation at a time.
            throw DebtAlreadyHasPaymentException.awaitingValidation();
        }
        if (normalized.amount().compareTo(debt.getOutstanding()) > 0) {
            throw new FieldValidationException(
                    Map.of("amount", EXCEEDS_OUTSTANDING.formatted(argentine(debt.getOutstanding()))));
        }

        // 022: the inherited flow follows the same rules as the new one, including the group's
        // "Pagos requiere validación" setting.
        boolean requiresValidation = groupPermissionsRepository.findByGroupId(debt.getGroupId())
                .map(GroupPermissions::isPaymentsRequireValidation)
                .orElse(false);
        String debtStatusBefore = debt.getStatus().name();
        if (requiresValidation) {
            debt.reportPayment(normalized.amount());
        }
        else {
            debt.applyPayment(normalized.amount());
        }
        String receiverName = nameOf(debt.getCreditorUserId());
        // legacy is just the origin marker "written by the inherited 018 endpoint"; it takes no part
        // in any index or decision (V25), so a REPORTED payment of this flow is decided like any other.
        Payment payment = paymentRepository.save(Payment.of(debt, normalized.amount(), normalized.date(),
                callerId, receiverName, idempotencyKey, true,
                requiresValidation ? PaymentStatus.REPORTED : PaymentStatus.CONFIRMED));

        if (requiresValidation) {
            activityEventService.recordPaymentRegistered(debt.getGroupId(), callerId, new PaymentActivityDetails(
                    payment.getId(), payment.getAmount(), payment.getCurrency().name(), payment.getPayerName(),
                    payment.getReceiverName(), debt.getExpenseId(), debt.getId(),
                    debtStatusBefore, debt.getStatus().name()));
        }
        else {
            activityEventService.recordPaymentMade(debt.getGroupId(), callerId, new PaymentActivityDetails(
                    payment.getId(), payment.getAmount(), payment.getCurrency().name(), payment.getPayerName(),
                    payment.getReceiverName(), debt.getExpenseId(), debt.getId()));
        }

        return new Registration(toResponse(payment, debt, caller), true);
    }

    /**
     * Resolves the unique-index race after the failed transaction was rolled
     * back: the winner is re-read and compared with the same rules (007/017).
     */
    @Transactional(readOnly = true)
    public Registration resolveConcurrentRegistration(Long callerId, UUID idempotencyKey,
            RegisterPaymentRequest request) {
        Payment winner = paymentRepository.findByRegisteredByUserIdAndIdempotencyKey(callerId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency conflict without a persisted winner"));
        Debt debt = debtRepository.findById(winner.getDebtId()).orElseThrow(DebtNotFoundException::new);
        GroupMember caller = activeMembership(callerId, debt.getGroupId());
        NormalizedPayment normalized = normalize(request, expenseDateOf(debt));
        return replay(winner, normalized, caller);
    }

    private Registration replay(Payment existing, NormalizedPayment normalized, GroupMember caller) {
        if (!existing.getDebtId().equals(normalized.debtId())
                || existing.getAmount().compareTo(normalized.amount()) != 0
                || !existing.getPaymentDate().equals(normalized.date())) {
            throw new IdempotencyConflictException();
        }
        Debt debt = debtRepository.findById(existing.getDebtId()).orElseThrow(DebtNotFoundException::new);
        return new Registration(toResponse(existing, debt, caller), false);
    }

    private GroupMember activeMembership(Long userId, Long groupId) {
        return groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId)
                .filter(membership -> membership.getLeftAt() == null)
                .orElseThrow(DebtNotFoundException::new);
    }

    private LocalDate expenseDateOf(Debt debt) {
        return expenseRepository.findById(debt.getExpenseId()).map(GroupExpense::getExpenseDate).orElse(null);
    }

    private NormalizedPayment normalize(RegisterPaymentRequest request, LocalDate expenseDate) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        LocalDate date = validateInput(request, expenseDate, fieldErrors);
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }
        return new NormalizedPayment(request.getDebtId(), ExpenseAmountNormalizer.normalize(request.getAmount()),
                date);
    }

    /** Amount and date with the 007 rules, the minimum date of the expense and unknown fields (D4). */
    private static LocalDate validateInput(RegisterPaymentRequest request, LocalDate expenseDate,
            Map<String, String> fieldErrors) {
        String amountError = ExpenseAmountNormalizer.validationError(request.getAmount());
        if (amountError != null) {
            fieldErrors.put("amount", amountError);
        }
        LocalDate date = parseDate(request.getDate(), request.getDeviceTimeZone(), fieldErrors);
        if (date != null && expenseDate != null && date.isBefore(expenseDate)) {
            fieldErrors.put("date", BEFORE_EXPENSE);
        }
        for (String unknown : request.getUnknownProperties().keySet()) {
            fieldErrors.put(unknown, UNKNOWN_PROPERTY);
        }
        return date;
    }

    private static LocalDate parseDate(String raw, String deviceTimeZone, Map<String, String> fieldErrors) {
        if (raw == null || raw.isBlank()) {
            fieldErrors.put("date", ExpenseDateValidator.REQUIRED_MESSAGE);
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(raw);
        }
        catch (DateTimeParseException exception) {
            fieldErrors.put("date", DATE_FORMAT);
            return null;
        }
        String dateError = ExpenseDateValidator.validationError(date, deviceTimeZone);
        if (dateError != null) {
            fieldErrors.put("date", dateError);
            return null;
        }
        return date;
    }

    private PaymentResponse toResponse(Payment payment, Debt debt, GroupMember caller) {
        boolean canPay = debt.getStatus().isPayable()
                && permissionChecker.canRegisterPayment(caller.getRole(), caller.getId().getUserId(),
                        debt.getDebtorUserId(), debt.getCreditorUserId());
        return new PaymentResponse(payment.getId(), payment.getGroupId(), debt.getExpenseId(), payment.getDebtId(),
                payment.getAmount().toPlainString(), payment.getCurrency().name(),
                payment.getPaymentDate().toString(),
                new GroupExpenseResponse.Debtor(payment.getPayerType().name(), payment.getPayerUserId(),
                        payment.getPayerSimulatedProfileId(), payment.getPayerName()),
                new GroupExpenseResponse.Person(payment.getReceiverUserId(), payment.getReceiverName()),
                new GroupExpenseResponse.Person(payment.getRegisteredByUserId(),
                        nameOf(payment.getRegisteredByUserId())),
                payment.getCreatedAt(),
                GroupExpenseResponse.DebtView.of(debt, nameOf(debt.getCreditorUserId()), canPay),
                payment.getStatus().name());
    }

    /** Current visible name of a user (014 D4), falling back to the email as 017 does. */
    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(PaymentService::displayOrEmail).orElse(null);
    }

    private static String displayOrEmail(User user) {
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }

    /** Argentine money text without the symbol: {@code 2333.33} → {@code 2.333,33}. */
    static String argentine(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(amount);
    }
}
