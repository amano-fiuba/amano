package com.amano.backend.payment;

import static com.amano.backend.payment.PaymentFixtures.ANA;
import static com.amano.backend.payment.PaymentFixtures.EXPENSE_ID;
import static com.amano.backend.payment.PaymentFixtures.GROUP_ID;
import static com.amano.backend.payment.PaymentFixtures.JUAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseAmountNormalizer;
import com.amano.backend.expense.ExpenseDateValidator;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseRepository;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissions;
import com.amano.backend.groups.GroupPermissionsRepository;
import com.amano.backend.groups.GroupPermissionDeniedException;
import com.amano.backend.groups.PaymentActivityDetails;
import com.amano.backend.notification.NotificationEventPublisher;
import com.amano.backend.notification.NotificationType;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * 018 T019–T023 (US1, plan.md §Flujo del backend): access (404 neutral, 403),
 * input with the 007 rules and the minimum date, the balance cap, the state
 * transitions through {@code Debt.applyPayment}, idempotency and the
 * PAYMENT_MADE event.
 */
class PaymentServiceTest {

    private static final long CARLA = 30L;
    private static final long OUTSIDER = 99L;
    private static final UUID KEY = UUID.fromString("1b2c3d4e-5f60-4718-9a2b-3c4d5e6f7a8b");

    private DebtRepository debtRepository;
    private PaymentRepository paymentRepository;
    private GroupExpenseRepository expenseRepository;
    private GroupMemberRepository groupMemberRepository;
    private UserRepository userRepository;
    private ActivityEventService activityEventService;
    private GroupPermissionsRepository groupPermissionsRepository;
    private NotificationEventPublisher notificationPublisher;
    private PaymentService service;
    private Debt debt;

    @BeforeEach
    void setUp() {
        debtRepository = mock(DebtRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        expenseRepository = mock(GroupExpenseRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        userRepository = mock(UserRepository.class);
        activityEventService = mock(ActivityEventService.class);
        groupPermissionsRepository = mock(GroupPermissionsRepository.class);
        notificationPublisher = mock(NotificationEventPublisher.class);
        service = new PaymentService(debtRepository, paymentRepository, expenseRepository, groupMemberRepository,
                new GroupPermissionChecker(), userRepository, activityEventService, groupPermissionsRepository,
                notificationPublisher);

        debt = PaymentFixtures.juanDebt();
        when(debtRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(debt));
        when(debtRepository.findById(31L)).thenReturn(Optional.of(debt));
        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(PaymentFixtures.expense()));

        member(ANA, GroupMember.ROLE_ADMIN);
        member(JUAN, GroupMember.ROLE_MEMBER);
        member(CARLA, GroupMember.ROLE_MEMBER);
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, OUTSIDER)).thenReturn(Optional.empty());

        when(userRepository.findById(ANA)).thenReturn(Optional.of(user(ANA, "Ana", "López")));
        when(userRepository.findById(JUAN)).thenReturn(Optional.of(user(JUAN, "Juan", "Pérez")));
        when(userRepository.findById(CARLA)).thenReturn(Optional.of(user(CARLA, " ", null)));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 54L);
            ReflectionTestUtils.setField(payment, "createdAt", OffsetDateTime.parse("2026-11-05T18:40:00Z"));
            return payment;
        });
    }

    private void member(long userId, String role) {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, userId))
                .thenReturn(Optional.of(new GroupMember(userId, GROUP_ID, role)));
    }

    /** The group asks an administrator to validate payments (022). */
    private void requireValidation() {
        GroupPermissions permissions = new GroupPermissions();
        permissions.setPaymentsRequireValidation(true);
        when(groupPermissionsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(permissions));
    }

    private static User user(long id, String first, String last) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail("u" + id + "@amano.dev");
        return user;
    }

    private static RegisterPaymentRequest request(Long debtId, String amount, String date) {
        RegisterPaymentRequest request = new RegisterPaymentRequest();
        request.setDebtId(debtId);
        request.setAmount(amount);
        request.setDate(date);
        request.setDeviceTimeZone("America/Argentina/Buenos_Aires");
        return request;
    }

    private static RegisterPaymentRequest pay(String amount) {
        return request(31L, amount, "2026-11-05");
    }

    private Map<String, String> fieldErrorsOf(long caller, RegisterPaymentRequest request) {
        FieldValidationException exception = catchThrowableOfType(
                () -> service.register(caller, KEY, request), FieldValidationException.class);
        assertThat(exception).as("expected a 400").isNotNull();
        nothingWasWritten();
        return exception.getFields();
    }

    private void nothingWasWritten() {
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(activityEventService);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("0.00");
    }

    // ── Acceso (T019) ──────────────────────────────────────────────────────

    @Test
    void anUnknownDebtIsTheNeutral404() {
        when(debtRepository.findByIdForUpdate(77L)).thenReturn(Optional.empty());

        assertThat(catchThrowableOfType(() -> service.register(JUAN, KEY, request(77L, "10.00", "2026-11-05")),
                DebtNotFoundException.class)).hasMessage(DebtNotFoundException.MESSAGE);
        verifyNoInteractions(paymentRepository, activityEventService);
    }

    @Test
    void aNonMemberGetsTheSame404() {
        assertThat(catchThrowableOfType(() -> service.register(OUTSIDER, KEY, pay("10.00")),
                DebtNotFoundException.class)).isNotNull();
        nothingWasWritten();
    }

    @Test
    void aMemberWhoLeftGetsTheSame404() {
        GroupMember left = new GroupMember(JUAN, GROUP_ID, GroupMember.ROLE_MEMBER);
        left.setLeftAt(OffsetDateTime.now());
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, JUAN)).thenReturn(Optional.of(left));

        assertThat(catchThrowableOfType(() -> service.register(JUAN, KEY, pay("10.00")),
                DebtNotFoundException.class)).isNotNull();
    }

    @Test
    void aThirdMemberCannotRegisterThePayment() {
        assertThat(catchThrowableOfType(() -> service.register(CARLA, KEY, pay("10.00")),
                GroupPermissionDeniedException.class)).hasMessage(PaymentService.NOT_ALLOWED);
        nothingWasWritten();
    }

    @Test
    void theDebtorTheCreditorAndAnAdminCanRegister() {
        assertThat(service.register(JUAN, KEY, pay("10.00")).created()).isTrue();
        assertThat(service.register(ANA, UUID.randomUUID(), pay("10.00")).created()).isTrue();
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("20.00");
    }

    // ── Entrada (T020) ─────────────────────────────────────────────────────

    @Test
    void theAmountFollowsThe007Rules() {
        assertThat(fieldErrorsOf(JUAN, pay(null))).containsEntry("amount", ExpenseAmountNormalizer.REQUIRED_MESSAGE);
        assertThat(fieldErrorsOf(JUAN, pay("0"))).containsKey("amount");
        assertThat(fieldErrorsOf(JUAN, pay("-5"))).containsKey("amount");
        assertThat(fieldErrorsOf(JUAN, pay("1.234"))).containsEntry("amount", ExpenseAmountNormalizer.DECIMALS_MESSAGE);
        assertThat(fieldErrorsOf(JUAN, pay("1000000000.00"))).containsKey("amount");
        assertThat(fieldErrorsOf(JUAN, pay("abc"))).containsKey("amount");
    }

    @Test
    void theAmountCannotExceedTheOutstandingBalance() {
        assertThat(fieldErrorsOf(JUAN, pay("3333.34")))
                .containsExactly(Map.entry("amount", "El pago no puede superar el saldo de $ 3.333,33"));
    }

    @Test
    void theDateFollowsThe007RulesAndCannotPrecedeTheExpense() {
        assertThat(fieldErrorsOf(JUAN, request(31L, "10.00", null)))
                .containsEntry("date", ExpenseDateValidator.REQUIRED_MESSAGE);
        assertThat(fieldErrorsOf(JUAN, request(31L, "10.00", "05/11/2026")))
                .containsEntry("date", PaymentService.DATE_FORMAT);
        assertThat(fieldErrorsOf(JUAN, request(31L, "10.00", "2999-01-01")))
                .containsEntry("date", ExpenseDateValidator.FUTURE_MESSAGE);
        assertThat(fieldErrorsOf(JUAN, request(31L, "10.00", "2026-10-31")))
                .containsEntry("date", PaymentService.BEFORE_EXPENSE);
    }

    @Test
    void anAnticipatedSystemOccurrenceCanBePaidBeforeItsExpenseDate() {
        GroupExpense occurrence = com.amano.backend.payment.PaymentFixtures.expense();
        occurrence.setRecurrenceSourceId(EXPENSE_ID + 1);
        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(occurrence));

        assertThat(service.register(JUAN, KEY, request(31L, "10.00", "2026-10-31")).created()).isTrue();
    }

    @Test
    void thePaymentMayBeOnTheExpenseDate() {
        assertThat(service.register(JUAN, KEY, request(31L, "10.00", "2026-11-01")).created()).isTrue();
    }

    @Test
    void aMissingDebtIsReportedWithTheOtherFieldErrors() {
        Map<String, String> fields = fieldErrorsOf(JUAN, request(null, "", "2999-01-01"));

        assertThat(fields).containsEntry("debtId", PaymentService.DEBT_REQUIRED)
                .containsKey("amount").containsKey("date");
        verify(debtRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void unknownFieldsAreRejected() {
        RegisterPaymentRequest request = pay("10.00");
        request.putUnknownProperty("payerUserId", 7);

        assertThat(fieldErrorsOf(JUAN, request)).containsEntry("payerUserId", PaymentService.UNKNOWN_PROPERTY);
    }

    @Test
    void anExpenseThatCannotBeReadSkipsTheMinimumDate() {
        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.empty());

        assertThat(service.register(JUAN, KEY, request(31L, "10.00", "2020-01-01")).created()).isTrue();
    }

    // ── Estado (T021) ──────────────────────────────────────────────────────

    @Test
    void aPartialPaymentLeavesThDebtPartiallyPaid() {
        PaymentResponse response = service.register(JUAN, KEY, pay("1000.00")).payment();

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        assertThat(response.debt().status()).isEqualTo("PARTIALLY_PAID");
        assertThat(response.debt().paidAmount()).isEqualTo("1000.00");
        assertThat(response.debt().outstanding()).isEqualTo("2333.33");
        assertThat(response.debt().canRegisterPayment()).isTrue();
        verify(debtRepository).findByIdForUpdate(31L);
        verify(debtRepository, never()).save(any());
    }

    @Test
    void paymentsUpToTheBalancePayTheDebt() {
        service.register(JUAN, KEY, pay("1000.00"));
        PaymentResponse response = service.register(JUAN, UUID.randomUUID(), pay("2333.33")).payment();

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
        assertThat(response.debt().outstanding()).isEqualTo("0.00");
        assertThat(response.debt().canRegisterPayment()).isFalse();
    }

    @Test
    void theWholeBalanceAtOncePaysThePendingDebt() {
        service.register(JUAN, KEY, pay("3333.33"));

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
        verify(notificationPublisher, org.mockito.Mockito.atLeastOnce()).publishSafely(argThat(event ->
                event.type() == NotificationType.PAYMENT_RECORDED));
    }

    @Test
    void the018FlowAlwaysMarksItsPaymentAsLegacyEvenWhenItSettlesTheDebt() {
        service.register(JUAN, KEY, pay("3333.33"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().isLegacy()).isTrue();
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID);
    }

    // ── 022: the inherited flow follows the validation setting ───────────────

    @Test
    void withValidationAPartialPaymentIsReportedAndThePaidAmountIsNotTouched() {
        requireValidation();

        PaymentResponse response = service.register(JUAN, KEY, pay("1000.00")).payment();

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(response.status()).isEqualTo("REPORTED");
        assertThat(response.debt().status()).isEqualTo("PAYMENT_REPORTED");
        assertThat(response.debt().outstanding()).isEqualTo("3333.33");
        assertThat(response.debt().canRegisterPayment()).isFalse();
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.REPORTED);
        // legacy stays the origin marker of this endpoint.
        assertThat(saved.getValue().isLegacy()).isTrue();
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void withValidationTheHistoryRecordsTheReportedPaymentWithItsAmountAndTheStatusChange() {
        requireValidation();

        service.register(JUAN, KEY, pay("1000.00"));

        ArgumentCaptor<PaymentActivityDetails> details = ArgumentCaptor.forClass(PaymentActivityDetails.class);
        verify(activityEventService).recordPaymentRegistered(eq(GROUP_ID), eq(JUAN), details.capture());
        verify(activityEventService, never()).recordPaymentMade(any(), any(), any());
        assertThat(details.getValue()).isEqualTo(new PaymentActivityDetails(54L, new BigDecimal("1000.00"), "ARS",
                "Juan Pérez", "Ana López", EXPENSE_ID, 31L, "PENDING", "PAYMENT_REPORTED"));
    }

    @Test
    void whileAPaymentWaitsForValidationAnotherOneAnswers409WithTheVoseoMessage() {
        requireValidation();
        service.register(JUAN, KEY, pay("1000.00"));

        assertThat(catchThrowableOfType(() -> service.register(ANA, UUID.randomUUID(), pay("500.00")),
                DebtAlreadyHasPaymentException.class))
                .hasMessage("Esta deuda ya tiene un pago esperando validación: esperá a que un administrador lo decida");
        verify(paymentRepository, org.mockito.Mockito.times(1)).save(any());
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAYMENT_REPORTED);
    }

    @Test
    void withoutValidationThePaymentIsConfirmedAtOnceAsBefore() {
        PaymentResponse response = service.register(JUAN, KEY, pay("1000.00")).payment();

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
        verify(activityEventService).recordPaymentMade(eq(GROUP_ID), eq(JUAN), any());
        verify(activityEventService, never()).recordPaymentRegistered(any(), any(), any());
    }

    @Test
    void anOverdueDebtWithAPartialPaymentBecomesPartiallyPaid() {
        org.springframework.test.util.ReflectionTestUtils.setField(debt, "status", DebtStatus.OVERDUE);

        service.register(JUAN, KEY, pay("1000.00"));

        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PARTIALLY_PAID);
    }

    @Test
    void aPaidDebtAnswers409WithoutWriting() {
        debt.applyPayment(new BigDecimal("3333.33"));

        assertThat(catchThrowableOfType(() -> service.register(JUAN, KEY, pay("1.00")),
                DebtAlreadyPaidException.class)).hasMessage(DebtAlreadyPaidException.MESSAGE);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(activityEventService);
    }

    // ── Respuesta e historial (T023) ───────────────────────────────────────

    @Test
    void theResponseCopiesTheDebtAndTheHistoryRecordsTheSevenKeys() {
        PaymentResponse response = service.register(ANA, KEY, pay("1000.00")).payment();

        assertThat(response.id()).isEqualTo(54L);
        assertThat(response.groupId()).isEqualTo(GROUP_ID);
        assertThat(response.expenseId()).isEqualTo(EXPENSE_ID);
        assertThat(response.debtId()).isEqualTo(31L);
        assertThat(response.amount()).isEqualTo("1000.00");
        assertThat(response.currency()).isEqualTo("ARS");
        assertThat(response.date()).isEqualTo("2026-11-05");
        assertThat(response.payer().userId()).isEqualTo(JUAN);
        assertThat(response.payer().name()).isEqualTo("Juan Pérez");
        assertThat(response.receiver().name()).isEqualTo("Ana López");
        assertThat(response.registeredBy().userId()).isEqualTo(ANA);
        assertThat(response.createdAt()).isNotNull();

        ArgumentCaptor<PaymentActivityDetails> details = ArgumentCaptor.forClass(PaymentActivityDetails.class);
        verify(activityEventService).recordPaymentMade(eq(GROUP_ID), eq(ANA), details.capture());
        assertThat(details.getValue()).isEqualTo(new PaymentActivityDetails(54L, new BigDecimal("1000.00"), "ARS",
                "Juan Pérez", "Ana López", EXPENSE_ID, 31L));
    }

    @Test
    void aSimulatedDebtorIsPaidByTheCreditor() {
        Debt abuela = PaymentFixtures.abuelaDebt();
        when(debtRepository.findByIdForUpdate(32L)).thenReturn(Optional.of(abuela));

        PaymentResponse response = service.register(ANA, KEY, request(32L, "3333.33", "2026-11-05")).payment();

        assertThat(response.payer().type()).isEqualTo("SIMULATED");
        assertThat(response.payer().name()).isEqualTo("Abuela");
        assertThat(abuela.getStatus()).isEqualTo(DebtStatus.PAID);
    }

    @Test
    void aUserWithoutVisibleNameFallsBackToTheEmail() {
        when(userRepository.findById(ANA)).thenReturn(Optional.of(user(ANA, null, null)));

        PaymentResponse response = service.register(JUAN, KEY, pay("10.00")).payment();

        assertThat(response.receiver().name()).isEqualTo("u7@amano.dev");
    }

    // ── Idempotencia (T022) ────────────────────────────────────────────────

    private Payment existingPayment(String amount, String date) {
        Payment existing = Payment.of(debt, new BigDecimal(amount), java.time.LocalDate.parse(date), JUAN,
                "Ana López", KEY);
        ReflectionTestUtils.setField(existing, "id", 54L);
        when(paymentRepository.findByRegisteredByUserIdAndIdempotencyKey(JUAN, KEY))
                .thenReturn(Optional.of(existing));
        return existing;
    }

    @Test
    void theSameKeyAndDataReplayThePaymentWithoutApplyingItAgain() {
        existingPayment("1000.00", "2026-11-05");

        PaymentService.Registration replay = service.register(JUAN, KEY, pay("1000"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.payment().id()).isEqualTo(54L);
        nothingWasWritten();
    }

    @Test
    void theSameKeyWithOtherDataIs422() {
        existingPayment("1000.00", "2026-11-05");

        assertThat(catchThrowableOfType(() -> service.register(JUAN, KEY, pay("999.00")),
                IdempotencyConflictException.class)).isNotNull();
        assertThat(catchThrowableOfType(() -> service.register(JUAN, KEY, request(31L, "1000.00", "2026-11-04")),
                IdempotencyConflictException.class)).isNotNull();
        nothingWasWritten();
    }

    @Test
    void theSameKeyOnAnotherDebtIs422() {
        existingPayment("1000.00", "2026-11-05");
        Debt other = PaymentFixtures.abuelaDebt();
        when(debtRepository.findByIdForUpdate(32L)).thenReturn(Optional.of(other));
        member(JUAN, GroupMember.ROLE_ADMIN);

        assertThat(catchThrowableOfType(() -> service.register(JUAN, KEY, request(32L, "1000.00", "2026-11-05")),
                IdempotencyConflictException.class)).isNotNull();
    }

    @Test
    void theRaceIsResolvedByReReadingTheWinner() {
        existingPayment("1000.00", "2026-11-05");

        PaymentService.Registration resolved = service.resolveConcurrentRegistration(JUAN, KEY, pay("1000.00"));

        assertThat(resolved.created()).isFalse();
        assertThat(resolved.payment().debt().id()).isEqualTo(31L);
    }

    @Test
    void theRaceWithoutAWinnerIsAnError() {
        assertThat(catchThrowableOfType(() -> service.resolveConcurrentRegistration(JUAN, KEY, pay("1.00")),
                IllegalStateException.class)).isNotNull();
    }

    @Test
    void moneyIsFormattedTheArgentineWay() {
        assertThat(PaymentService.argentine(new BigDecimal("2333.33"))).isEqualTo("2.333,33");
        assertThat(PaymentService.argentine(new BigDecimal("0.5"))).isEqualTo("0,50");
        assertThat(PaymentService.argentine(new BigDecimal("1234567.89"))).isEqualTo("1.234.567,89");
    }
}
