package com.amano.backend.groupexpense.whistle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.BeanUtils;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

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
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of {@link DebtWhistleService} (030 T007, T021): validation order
 * (research.md D4), claim/send/mark flow (D3) and the 24 h limit. Fixed clock
 * on 15/11/2026 15:00 in Buenos Aires.
 */
class DebtWhistleServiceTest {

    private static final Long GROUP = 1L;
    private static final Long OTHER_GROUP = 2L;
    private static final Long ANA = 10L;
    private static final Long JUAN = 20L;
    private static final Long CARLA = 30L;
    private static final Long DEBT = 100L;
    private static final Long EXPENSE = 50L;
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final OffsetDateTime NOW = LocalDate.parse("2026-11-15").atTime(15, 0).atZone(ZONE)
            .toOffsetDateTime();

    private GroupRepository groupRepository;
    private GroupMemberRepository groupMemberRepository;
    private DebtRepository debtRepository;
    private GroupExpenseRepository expenseRepository;
    private UserRepository userRepository;
    private DebtWhistleRepository whistleRepository;
    private ReminderRecipients recipients;
    private ReminderMailService mailService;
    private ActivityEventService activityEventService;
    private DebtWhistleService service;
    private final List<DebtWhistle> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        debtRepository = mock(DebtRepository.class);
        expenseRepository = mock(GroupExpenseRepository.class);
        userRepository = mock(UserRepository.class);
        whistleRepository = mock(DebtWhistleRepository.class);
        recipients = mock(ReminderRecipients.class);
        mailService = mock(ReminderMailService.class);
        activityEventService = mock(ActivityEventService.class);
        Clock clock = Clock.fixed(NOW.toInstant(), ZONE);
        service = new DebtWhistleService(groupRepository, groupMemberRepository, debtRepository,
                expenseRepository, userRepository, whistleRepository, recipients, new ReminderMessageBuilder(),
                mailService, activityEventService, clock, mock(PlatformTransactionManager.class));
        saved.clear();

        when(groupRepository.findById(GROUP)).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findForUpdate(GROUP, ANA)).thenReturn(Optional.of(member(null)));
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP, JUAN)).thenReturn(Optional.of(member(null)));
        when(expenseRepository.findById(EXPENSE)).thenReturn(Optional.of(expense()));
        when(userRepository.findById(ANA)).thenReturn(Optional.of(user(ANA, "Ana", "López")));
        when(recipients.emailOf(any(Debt.class))).thenReturn(Optional.of("juan@example.com"));
        when(whistleRepository.findBlocking(eq(GROUP), eq(ANA), eq(JUAN), any())).thenReturn(List.of());
        when(whistleRepository.save(any(DebtWhistle.class))).thenAnswer(invocation -> {
            DebtWhistle whistle = invocation.getArgument(0);
            ReflectionTestUtils.setField(whistle, "id", 7L);
            saved.add(whistle);
            return whistle;
        });
        when(whistleRepository.markSent(anyLong(), any())).thenReturn(1);
        when(whistleRepository.markFailed(anyLong(), any())).thenReturn(1);
        debt(DebtStatus.PENDING, ParticipantType.REGISTERED, GROUP);
    }

    // ---- US1: envío ----

    @ParameterizedTest
    @EnumSource(value = DebtStatus.class, names = { "PENDING", "PARTIALLY_PAID", "OVERDUE" })
    void theCreditorWhistlesAnOpenDebtAndOneMailIsSent(DebtStatus status) throws Exception {
        Debt debt = debt(status, ParticipantType.REGISTERED, GROUP);

        DebtWhistleResponse response = service.whistle(ANA, GROUP, DEBT);

        assertThat(response.whistleId()).isEqualTo(7L);
        assertThat(response.debtId()).isEqualTo(DEBT);
        assertThat(response.debtorName()).isEqualTo("Juan Pérez");
        assertThat(response.sentAt()).isEqualTo(NOW);
        assertThat(response.nextAvailableAt()).isEqualTo(NOW.plusHours(24));
        assertThat(saved).singleElement().satisfies(whistle -> {
            assertThat(whistle.getStatus()).isEqualTo(DebtWhistleStatus.SENDING);
            assertThat(whistle.getCreatedAt()).isEqualTo(NOW);
            assertThat(whistle.getGroupId()).isEqualTo(GROUP);
            assertThat(whistle.getDebtId()).isEqualTo(DEBT);
            assertThat(whistle.getCreditorUserId()).isEqualTo(ANA);
            assertThat(whistle.getDebtorUserId()).isEqualTo(JUAN);
        });
        verify(mailService).send(eq("juan@example.com"), any(ReminderMessageBuilder.Mail.class));
        verify(whistleRepository).markSent(7L, NOW);
        verify(whistleRepository, never()).markFailed(anyLong(), any());
        assertThat(debt.getStatus()).isEqualTo(status);
        assertThat(debt.getPaidAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void theMailCarriesTheOutstandingBalanceAndTheCreditorName() throws Exception {
        service.whistle(ANA, GROUP, DEBT);

        var captor = org.mockito.ArgumentCaptor.forClass(ReminderMessageBuilder.Mail.class);
        verify(mailService).send(eq("juan@example.com"), captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("Ana López te recuerda una deuda - Viaje");
        assertThat(captor.getValue().body()).contains("7000.00 ARS").contains("«Cena»");
    }

    @Test
    void aGroupThatDoesNotExistIsTheNeutral404() {
        when(groupRepository.findById(GROUP)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT)).isInstanceOf(GroupNotFoundException.class);
        verifyNothingSent();
    }

    @Test
    void aCallerThatIsNotAnActiveMemberIsTheNeutral404() {
        when(groupMemberRepository.findForUpdate(GROUP, ANA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT)).isInstanceOf(GroupNotFoundException.class);

        when(groupMemberRepository.findForUpdate(GROUP, ANA)).thenReturn(Optional.of(member(NOW.minusDays(1))));
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT)).isInstanceOf(GroupNotFoundException.class);
        verifyNothingSent();
    }

    @Test
    void aMissingDebtOrADebtOfAnotherGroupIsTheNeutral404() {
        when(debtRepository.findById(DEBT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT)).isInstanceOf(GroupNotFoundException.class);

        debt(DebtStatus.PENDING, ParticipantType.REGISTERED, OTHER_GROUP);
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT)).isInstanceOf(GroupNotFoundException.class);
        verifyNothingSent();
    }

    @Test
    void someoneWhoIsNotTheCreditorGets403() {
        when(groupMemberRepository.findForUpdate(GROUP, CARLA)).thenReturn(Optional.of(member(null)));
        when(groupMemberRepository.findForUpdate(GROUP, JUAN)).thenReturn(Optional.of(member(null)));

        assertThatThrownBy(() -> service.whistle(CARLA, GROUP, DEBT))
                .isInstanceOf(WhistleNotCreditorException.class);
        assertThatThrownBy(() -> service.whistle(JUAN, GROUP, DEBT))
                .isInstanceOf(WhistleNotCreditorException.class);
        verifyNothingSent();
    }

    @ParameterizedTest
    @EnumSource(value = DebtStatus.class, names = { "PAID", "VERIFIED" })
    void aSettledDebtIs409(DebtStatus status) {
        debt(status, ParticipantType.REGISTERED, GROUP);
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(DebtNotWhistleableException.class)
                .hasMessage("La deuda ya no tiene saldo pendiente")
                .extracting("code").isEqualTo(DebtNotWhistleableException.NOT_WHISTLEABLE);
        verifyNothingSent();
    }

    @Test
    void aDebtWithAReportedPaymentIs409WithItsOwnMessage() {
        debt(DebtStatus.PAYMENT_REPORTED, ParticipantType.REGISTERED, GROUP);
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(DebtNotWhistleableException.class)
                .hasMessage("La deuda tiene un pago esperando validación");
        verifyNothingSent();
    }

    @Test
    void aSimulatedDebtorIs422() {
        debt(DebtStatus.PENDING, ParticipantType.SIMULATED, GROUP);
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(SimulatedDebtorWhistleException.class)
                .hasMessage("Los perfiles simulados no reciben silbidos");
        verifyNothingSent();
    }

    @Test
    void aDebtorThatLeftTheGroupIs409() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP, JUAN))
                .thenReturn(Optional.of(member(NOW.minusDays(2))));
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(DebtNotWhistleableException.class)
                .hasMessage("Juan Pérez ya no forma parte del grupo")
                .extracting("code").isEqualTo(DebtNotWhistleableException.DEBTOR_NOT_MEMBER);

        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP, JUAN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(DebtNotWhistleableException.class);
        verifyNothingSent();
    }

    @Test
    void anSmtpFailureMarksTheAttemptFailedAndIs503() throws Exception {
        doThrow(new MailSendException("smtp down")).when(mailService).send(anyString(),
                any(ReminderMessageBuilder.Mail.class));

        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(WhistleDeliveryException.class)
                .hasMessage("No pudimos enviar el aviso. Probá de nuevo en unos minutos");
        verify(whistleRepository).markFailed(7L, NOW);
        verify(whistleRepository, never()).markSent(anyLong(), any());
        verify(activityEventService, never()).recordDebtWhistled(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void aDebtorWithoutResolvableEmailIs503WithoutSending() throws Exception {
        when(recipients.emailOf(any(Debt.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT)).isInstanceOf(WhistleDeliveryException.class);
        verify(whistleRepository).markFailed(7L, NOW);
        verify(mailService, never()).send(anyString(), any());
    }

    @Test
    void theCreditorNameFallsBackToTheEmailAndTheConceptToTheDefault() throws Exception {
        User noName = user(ANA, null, null);
        noName.setEmail("ana@example.com");
        when(userRepository.findById(ANA)).thenReturn(Optional.of(noName));
        GroupExpense untitled = expense();
        untitled.setDescription(null);
        when(expenseRepository.findById(EXPENSE)).thenReturn(Optional.of(untitled));

        service.whistle(ANA, GROUP, DEBT);

        var captor = org.mockito.ArgumentCaptor.forClass(ReminderMessageBuilder.Mail.class);
        verify(mailService).send(eq("juan@example.com"), captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("ana@example.com te recuerda una deuda - Viaje");
        assertThat(captor.getValue().body()).contains("«" + ReminderMessageBuilder.DEFAULT_CONCEPT + "»");
    }

    // ---- US2: límite de 24 h (T021) ----

    @Test
    void aSentWhistleOneSecondBeforeTheWindowEndsStillBlocks() throws Exception {
        whistleAt(NOW.minusHours(24).plusSeconds(1));

        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(WhistleRateLimitedException.class)
                .hasMessage("Ya le silbaste a Juan Pérez en las últimas 24 horas")
                .extracting("retryAfterSeconds").isEqualTo(1L);
        verify(whistleRepository, never()).save(any());
        verify(mailService, never()).send(anyString(), any());
    }

    @Test
    void theWindowIsAskedForTheLast24HoursOfThePair() throws Exception {
        service.whistle(ANA, GROUP, DEBT);
        verify(whistleRepository).findBlocking(GROUP, ANA, JUAN, NOW.minusHours(24));
    }

    @Test
    void retryAfterIsRoundedUpToTheSecond() {
        whistleAt(NOW.minusHours(6).minusMinutes(20).plusNanos(500_000_000));

        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(WhistleRateLimitedException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(Duration.ofHours(17).plusMinutes(40).plusSeconds(1).toSeconds());
    }

    @Test
    void theNewestBlockingAttemptDefinesTheWait() {
        DebtWhistle newest = DebtWhistle.sending(GROUP, DEBT, ANA, JUAN, NOW.minusHours(1));
        DebtWhistle older = DebtWhistle.sending(GROUP, DEBT, ANA, JUAN, NOW.minusHours(20));
        when(whistleRepository.findBlocking(eq(GROUP), eq(ANA), eq(JUAN), any())).thenReturn(List.of(newest, older));

        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(WhistleRateLimitedException.class)
                .extracting("retryAfterSeconds").isEqualTo(Duration.ofHours(23).toSeconds());
    }

    @Test
    void validationRulesComeBeforeTheLimit() {
        whistleAt(NOW.minusMinutes(5));
        debt(DebtStatus.PAID, ParticipantType.REGISTERED, GROUP);
        assertThatThrownBy(() -> service.whistle(ANA, GROUP, DEBT))
                .isInstanceOf(DebtNotWhistleableException.class);
    }

    // ---- US3: historial (T026, unitario) ----

    @Test
    void aDeliveredWhistleIsRecordedInTheHistoryWithTheCreditorAsActor() throws Exception {
        service.whistle(ANA, GROUP, DEBT);

        verify(activityEventService).recordDebtWhistled(GROUP, ANA, 7L, DEBT, EXPENSE, new BigDecimal("7000.00"),
                "ARS", "Juan Pérez", "Ana López", "Cena");
    }

    @Test
    void anAttemptAlreadyDecidedIsNotRecordedTwice() throws Exception {
        when(whistleRepository.markSent(anyLong(), any())).thenReturn(0);

        service.whistle(ANA, GROUP, DEBT);

        verify(activityEventService, never()).recordDebtWhistled(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    // ---- fixtures ----

    private void verifyNothingSent() {
        try {
            verify(mailService, never()).send(anyString(), any());
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
        verify(whistleRepository, never()).save(any());
    }

    private void whistleAt(OffsetDateTime createdAt) {
        when(whistleRepository.findBlocking(eq(GROUP), eq(ANA), eq(JUAN), any()))
                .thenReturn(List.of(DebtWhistle.sending(GROUP, DEBT, ANA, JUAN, createdAt)));
    }

    private Debt debt(DebtStatus status, ParticipantType debtorType, Long groupId) {
        Debt debt = BeanUtils.instantiateClass(Debt.class);
        ReflectionTestUtils.setField(debt, "id", DEBT);
        ReflectionTestUtils.setField(debt, "groupId", groupId);
        ReflectionTestUtils.setField(debt, "expenseId", EXPENSE);
        ReflectionTestUtils.setField(debt, "debtorType", debtorType);
        ReflectionTestUtils.setField(debt, "debtorUserId", debtorType == ParticipantType.REGISTERED ? JUAN : null);
        ReflectionTestUtils.setField(debt, "debtorSimulatedProfileId",
                debtorType == ParticipantType.SIMULATED ? 99L : null);
        ReflectionTestUtils.setField(debt, "debtorName", "Juan Pérez");
        ReflectionTestUtils.setField(debt, "creditorUserId", ANA);
        ReflectionTestUtils.setField(debt, "currency", Currency.ARS);
        ReflectionTestUtils.setField(debt, "amount", new BigDecimal("8000.00"));
        ReflectionTestUtils.setField(debt, "paidAmount", new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(debt, "status", status);
        when(debtRepository.findById(DEBT)).thenReturn(Optional.of(debt));
        return debt;
    }

    private static Group group() {
        Group group = new Group();
        ReflectionTestUtils.setField(group, "id", GROUP);
        group.setName("Viaje");
        group.setPrimaryCurrency(Currency.ARS);
        return group;
    }

    private static GroupMember member(OffsetDateTime leftAt) {
        GroupMember member = new GroupMember();
        member.setRole(GroupMember.ROLE_MEMBER);
        ReflectionTestUtils.setField(member, "leftAt", leftAt);
        return member;
    }

    private static GroupExpense expense() {
        GroupExpense expense = new GroupExpense();
        ReflectionTestUtils.setField(expense, "id", EXPENSE);
        expense.setDescription("Cena");
        expense.setDueDate(LocalDate.parse("2026-11-20"));
        return expense;
    }

    private static User user(Long id, String first, String last) {
        User user = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "firstName", first);
        ReflectionTestUtils.setField(user, "lastName", last);
        ReflectionTestUtils.setField(user, "email", "ana@example.com");
        return user;
    }
}
