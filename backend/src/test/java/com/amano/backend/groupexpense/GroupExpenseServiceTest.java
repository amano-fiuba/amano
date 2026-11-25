package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseAmountNormalizer;
import com.amano.backend.expense.ExpenseDateValidator;
import com.amano.backend.expense.ExpenseDescriptionNormalizer;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.ExpenseActivityDetails;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupNotFoundException;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissionDeniedException;
import com.amano.backend.groups.GroupPermissions;
import com.amano.backend.groups.GroupPermissionsRepository;
import com.amano.backend.groups.SimulatedProfile;
import com.amano.backend.groups.SimulatedProfileRepository;
import com.amano.backend.notification.NotificationEventPublisher;
import com.amano.backend.notification.NotificationType;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * 017 T014 (US1): registration of a group expense — validation with the 007
 * rules, payer and participants of the group, the two V9 casillas,
 * idempotency, shares and PENDING debts with copied names, and the
 * EXPENSE_CREATED history event (FR-002..FR-015).
 */
class GroupExpenseServiceTest {

    private static final long GROUP_ID = 3L;
    private static final long ANA = 7L;
    private static final long JUAN = 15L;
    private static final long PEDRO_EX_MEMBER = 20L;
    private static final long ABUELA = 4L;
    private static final long TIO = 5L;
    private static final long FOREIGN_PROFILE = 9L;
    private static final UUID KEY = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");

    private GroupExpenseRepository expenseRepository;
    private GroupExpenseShareRepository shareRepository;
    private DebtRepository debtRepository;
    private GroupMemberRepository groupMemberRepository;
    private GroupPermissionsRepository groupPermissionsRepository;
    private SimulatedProfileRepository simulatedProfileRepository;
    private UserRepository userRepository;
    private ActivityEventService activityEventService;
    private GroupPermissions permissions;
    private NotificationEventPublisher notificationPublisher;
    private GroupExpenseService service;

    private final List<GroupExpenseShare> savedShares = new ArrayList<>();

    @BeforeEach
    void setUp() {
        expenseRepository = Mockito.mock(GroupExpenseRepository.class);
        shareRepository = Mockito.mock(GroupExpenseShareRepository.class);
        debtRepository = Mockito.mock(DebtRepository.class);
        groupMemberRepository = Mockito.mock(GroupMemberRepository.class);
        groupPermissionsRepository = Mockito.mock(GroupPermissionsRepository.class);
        simulatedProfileRepository = Mockito.mock(SimulatedProfileRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        activityEventService = Mockito.mock(ActivityEventService.class);
        notificationPublisher = Mockito.mock(NotificationEventPublisher.class);
        service = new GroupExpenseService(expenseRepository, shareRepository, debtRepository,
                groupMemberRepository, groupPermissionsRepository, new GroupPermissionChecker(),
                simulatedProfileRepository, userRepository, activityEventService, notificationPublisher);

        GroupMember ana = new GroupMember(ANA, GROUP_ID, GroupMember.ROLE_ADMIN);
        GroupMember juan = new GroupMember(JUAN, GROUP_ID, GroupMember.ROLE_MEMBER);
        GroupMember pedro = new GroupMember(PEDRO_EX_MEMBER, GROUP_ID, GroupMember.ROLE_MEMBER);
        pedro.setLeftAt(OffsetDateTime.now());
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, ANA)).thenReturn(Optional.of(ana));
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, JUAN)).thenReturn(Optional.of(juan));
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, PEDRO_EX_MEMBER))
                .thenReturn(Optional.of(pedro));
        when(groupMemberRepository.findByIdGroupIdAndLeftAtIsNull(GROUP_ID)).thenReturn(List.of(ana, juan));

        when(simulatedProfileRepository.findByGroupId(GROUP_ID))
                .thenReturn(List.of(profile(ABUELA, "Abuela"), profile(TIO, "Tío")));

        permissions = new GroupPermissions();
        permissions.setGroupId(GROUP_ID);
        when(groupPermissionsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(permissions));

        when(userRepository.findAllById(anyIterable()))
                .thenReturn(List.of(user(ANA, "Ana", "López"), user(JUAN, "Juan", "Pérez")));

        when(expenseRepository.save(any(GroupExpense.class))).thenAnswer(invocation -> {
            GroupExpense expense = invocation.getArgument(0);
            ReflectionTestUtils.setField(expense, "id", 12L);
            ReflectionTestUtils.setField(expense, "createdAt", OffsetDateTime.parse("2026-11-05T15:20:00Z"));
            return expense;
        });
        AtomicLong shareIds = new AtomicLong(40);
        when(shareRepository.save(any(GroupExpenseShare.class))).thenAnswer(invocation -> {
            GroupExpenseShare share = invocation.getArgument(0);
            ReflectionTestUtils.setField(share, "id", shareIds.getAndIncrement());
            savedShares.add(share);
            return share;
        });
        when(debtRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<Debt> debts = new ArrayList<>();
            invocation.<Iterable<Debt>>getArgument(0).forEach(debts::add);
            AtomicLong debtIds = new AtomicLong(31);
            debts.forEach(debt -> ReflectionTestUtils.setField(debt, "id", debtIds.getAndIncrement()));
            return debts;
        });
    }

    private static SimulatedProfile profile(Long id, String name) {
        SimulatedProfile profile = new SimulatedProfile();
        ReflectionTestUtils.setField(profile, "id", id);
        profile.setGroupId(GROUP_ID);
        profile.setName(name);
        profile.setEmail(name.toLowerCase() + "@amano.dev");
        return profile;
    }

    private static User user(Long id, String first, String last) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(first.toLowerCase() + "@amano.dev");
        return user;
    }

    private static CreateGroupExpenseRequest.Participant registered(Long userId) {
        return new CreateGroupExpenseRequest.Participant("REGISTERED", userId, null);
    }

    private static CreateGroupExpenseRequest.Participant simulated(Long profileId) {
        return new CreateGroupExpenseRequest.Participant("SIMULATED", null, profileId);
    }

    private static CreateGroupExpenseRequest.Participant registeredAmount(Long userId, String amount) {
        return new CreateGroupExpenseRequest.Participant("REGISTERED", userId, null, amount, null);
    }

    private static CreateGroupExpenseRequest.Participant simulatedAmount(Long profileId, String amount) {
        return new CreateGroupExpenseRequest.Participant("SIMULATED", null, profileId, amount, null);
    }

    private static CreateGroupExpenseRequest.Participant registeredPercentage(Long userId, String percentage) {
        return new CreateGroupExpenseRequest.Participant("REGISTERED", userId, null, null, percentage);
    }

    private static CreateGroupExpenseRequest.Participant simulatedPercentage(Long profileId, String percentage) {
        return new CreateGroupExpenseRequest.Participant("SIMULATED", null, profileId, null, percentage);
    }

    private static CreateGroupExpenseRequest splitRequest(String amount, Long payer, String splitMethod,
            CreateGroupExpenseRequest.Participant... participants) {
        CreateGroupExpenseRequest request = request(amount, payer, participants);
        request.setSplitMethod(splitMethod);
        return request;
    }

    private static CreateGroupExpenseRequest request(String amount, Long payer,
            CreateGroupExpenseRequest.Participant... participants) {
        CreateGroupExpenseRequest request = new CreateGroupExpenseRequest();
        request.setAmount(amount);
        request.setCurrency("ARS");
        request.setDate("2026-11-05");
        request.setDescription("Súper");
        request.setDeviceTimeZone("America/Argentina/Buenos_Aires");
        request.setPayerUserId(payer);
        request.setParticipants(java.util.Arrays.asList(participants));
        request.setCategory("FOOD");
        request.setType("ONE_TIME");
        request.setPaymentMethod("CASH");
        return request;
    }

    private CreateGroupExpenseRequest threeWay() {
        return request("10000.00", ANA, registered(ANA), registered(JUAN), simulated(ABUELA));
    }

    private CreateGroupExpenseRequest customThreeWay() {
        return splitRequest("10000.00", ANA, "CUSTOM",
                registeredAmount(ANA, "3000.00"), registeredAmount(JUAN, "6000.00"),
                simulatedAmount(ABUELA, "1000.00"));
    }

    @SuppressWarnings("unchecked")
    private List<Debt> savedDebts() {
        ArgumentCaptor<Iterable<Debt>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(debtRepository).saveAll(captor.capture());
        List<Debt> debts = new ArrayList<>();
        captor.getValue().forEach(debts::add);
        return debts;
    }

    private Map<String, String> fieldErrorsOf(CreateGroupExpenseRequest request) {
        FieldValidationException exception = catchThrowableOfType(
                () -> service.create(ANA, GROUP_ID, KEY, request), FieldValidationException.class);
        assertThat(exception).as("expected a 400").isNotNull();
        verify(expenseRepository, never()).save(any());
        Mockito.verifyNoInteractions(activityEventService);
        return exception.getFields();
    }

    private void nothingWasSaved() {
        verify(expenseRepository, never()).save(any());
        verify(shareRepository, never()).save(any());
        verify(debtRepository, never()).saveAll(anyIterable());
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void createsTheSharesAndOnePendingDebtPerParticipantOtherThanThePayer() {
        GroupExpenseService.Registration registration = service.create(ANA, GROUP_ID, KEY, threeWay());

        assertThat(registration.created()).isTrue();
        GroupExpenseResponse response = registration.expense();
        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.groupId()).isEqualTo(GROUP_ID);
        assertThat(response.amount()).isEqualTo("10000.00");
        assertThat(response.currency()).isEqualTo("ARS");
        assertThat(response.date()).isEqualTo("2026-11-05");
        assertThat(response.description()).isEqualTo("Súper");
        assertThat(response.payer()).isEqualTo(new GroupExpenseResponse.Person(ANA, "Ana López"));
        assertThat(response.createdBy()).isEqualTo(new GroupExpenseResponse.Person(ANA, "Ana López"));
        assertThat(response.shares()).extracting(GroupExpenseResponse.Share::name, GroupExpenseResponse.Share::amount,
                GroupExpenseResponse.Share::isPayer)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ana López", "3333.34", true),
                        org.assertj.core.groups.Tuple.tuple("Juan Pérez", "3333.33", false),
                        org.assertj.core.groups.Tuple.tuple("Abuela", "3333.33", false));

        List<Debt> debts = savedDebts();
        assertThat(debts).hasSize(2).allSatisfy(debt -> {
            assertThat(debt.getStatus()).isEqualTo(DebtStatus.PENDING);
            assertThat(debt.getAmount()).isEqualByComparingTo("3333.33");
            assertThat(debt.getPaidAmount()).isEqualByComparingTo("0");
            assertThat(debt.getCreditorUserId()).isEqualTo(ANA);
            assertThat(debt.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(debt.getExpenseId()).isEqualTo(12L);
        });
        assertThat(debts).extracting(Debt::getDebtorName).containsExactly("Juan Pérez", "Abuela");
        assertThat(debts.get(0).getDebtorUserId()).isEqualTo(JUAN);
        assertThat(debts.get(1).getDebtorSimulatedProfileId()).isEqualTo(ABUELA);
        assertThat(response.debts()).extracting(GroupExpenseResponse.DebtView::status)
                .containsExactly("PENDING", "PENDING");
        assertThat(response.debts().get(0).outstanding()).isEqualTo("3333.33");
        verify(notificationPublisher).publishSafely(argThat(event ->
                event.type() == NotificationType.GROUP_EXPENSE && event.userId().equals(ANA)));
        verify(notificationPublisher).publishSafely(argThat(event ->
                event.type() == NotificationType.GROUP_EXPENSE && event.userId().equals(JUAN)));
    }

    @Test
    void theExpenseKeepsTheSessionUserAsCreatorAndTheNormalizedValues() {
        CreateGroupExpenseRequest request = threeWay();
        request.setAmount("10000.5");
        request.setDescription("  Súper  ");

        service.create(ANA, GROUP_ID, KEY, request);

        ArgumentCaptor<GroupExpense> captor = ArgumentCaptor.forClass(GroupExpense.class);
        verify(expenseRepository).save(captor.capture());
        GroupExpense saved = captor.getValue();
        assertThat(saved.getCreatedByUserId()).isEqualTo(ANA);
        assertThat(saved.getPayerUserId()).isEqualTo(ANA);
        assertThat(saved.getAmount().toPlainString()).isEqualTo("10000.50");
        assertThat(saved.getDescription()).isEqualTo("Súper");
        assertThat(saved.getExpenseDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(saved.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(savedShares).extracting(GroupExpenseShare::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void withoutThePayerTheRemainderGoesToTheFirstParticipants() {
        GroupExpenseResponse response = service.create(ANA, GROUP_ID, KEY,
                request("100.00", ANA, registered(JUAN), simulated(ABUELA), simulated(TIO))).expense();

        assertThat(response.shares()).extracting(GroupExpenseResponse.Share::amount)
                .containsExactly("33.34", "33.33", "33.33");
        assertThat(savedDebts()).hasSize(3);
    }

    @Test
    void theExpenseIsRecordedInTheGroupHistory() {
        service.create(ANA, GROUP_ID, KEY, threeWay());

        verify(activityEventService).recordExpenseCreated(GROUP_ID, ANA,
                new ExpenseActivityDetails(12L, new BigDecimal("10000.00"), "ARS", "Súper", ANA, "Ana López"));
    }

    @Test
    void invalidAmountCurrencyDateAndDescriptionUseThe007Messages() {
        CreateGroupExpenseRequest request = threeWay();
        request.setAmount("10.001");
        request.setCurrency("XYZ");
        request.setDate(LocalDate.now().plusDays(3).toString());
        request.setDescription("x".repeat(201));

        Map<String, String> fields = fieldErrorsOf(request);

        assertThat(fields).containsEntry("amount", ExpenseAmountNormalizer.DECIMALS_MESSAGE)
                .containsEntry("currency", "La moneda no es válida")
                .containsEntry("date", ExpenseDateValidator.FUTURE_MESSAGE)
                .containsEntry("description", ExpenseDescriptionNormalizer.LENGTH_MESSAGE);
    }

    @Test
    void missingRequiredValuesAreReportedPerField() {
        CreateGroupExpenseRequest request = new CreateGroupExpenseRequest();

        Map<String, String> fields = fieldErrorsOf(request);

        assertThat(fields).containsEntry("amount", ExpenseAmountNormalizer.REQUIRED_MESSAGE)
                .containsEntry("currency", "La moneda es obligatoria")
                .containsEntry("date", ExpenseDateValidator.REQUIRED_MESSAGE)
                .containsEntry("payerUserId", GroupExpenseService.PAYER_REQUIRED)
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_REQUIRED);
    }

    /** 027 T044 (FR-020): the 007 classification is required, validated and saved. */
    @Test
    void theClassificationIsSavedAndReturned() {
        CreateGroupExpenseRequest request = threeWay();
        request.setCategory("LEISURE");
        request.setType("RECURRING");
        request.setFrequency("MONTHLY");
        request.setPaymentMethod("CREDIT_CARD");

        GroupExpenseResponse response = service.create(ANA, GROUP_ID, KEY, request).expense();

        ArgumentCaptor<GroupExpense> captor = ArgumentCaptor.forClass(GroupExpense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(com.amano.backend.expense.ExpenseCategory.LEISURE);
        assertThat(captor.getValue().getExpenseType()).isEqualTo(com.amano.backend.expense.ExpenseType.RECURRING);
        assertThat(captor.getValue().getRecurrenceFrequency())
                .isEqualTo(com.amano.backend.expense.RecurrenceFrequency.MONTHLY);
        assertThat(captor.getValue().getRecurrenceStatus())
                .isEqualTo(com.amano.backend.expense.RecurrenceStatus.ACTIVE);
        assertThat(captor.getValue().getRecurrenceNextDate()).isNotNull();
        assertThat(captor.getValue().getPaymentMethod())
                .isEqualTo(com.amano.backend.expense.PaymentMethod.CREDIT_CARD);
        assertThat(response.category()).isEqualTo("LEISURE");
        assertThat(response.type()).isEqualTo("RECURRING");
        assertThat(response.paymentMethod()).isEqualTo("CREDIT_CARD");
    }

    @Test
    void theClassificationUsesThe007MessagesWhenMissingOrInvalid() {
        CreateGroupExpenseRequest missing = threeWay();
        missing.setCategory(null);
        missing.setType(" ");
        missing.setPaymentMethod(null);
        assertThat(fieldErrorsOf(missing))
                .containsEntry("category", "La categoría es obligatoria")
                .containsEntry("type", "El tipo de gasto es obligatorio")
                .containsEntry("paymentMethod", "El medio de pago es obligatorio");

        CreateGroupExpenseRequest invalid = threeWay();
        invalid.setCategory("PARTY");
        invalid.setType("WEEKLY");
        invalid.setPaymentMethod("BITCOIN");
        assertThat(fieldErrorsOf(invalid))
                .containsEntry("category", "La categoría no es válida")
                .containsEntry("type", "El tipo de gasto no es válido")
                .containsEntry("paymentMethod", "El medio de pago no es válido");
    }

    @Test
    void aRecurringSharedExpenseRequiresAFrequency() {
        CreateGroupExpenseRequest request = threeWay();
        request.setType("RECURRING");

        assertThat(fieldErrorsOf(request))
                .containsEntry("frequency", GroupExpenseService.FREQUENCY_REQUIRED);
    }

    @Test
    void aOneTimeSharedExpenseRejectsAFrequency() {
        CreateGroupExpenseRequest request = threeWay();
        request.setFrequency("WEEKLY");

        assertThat(fieldErrorsOf(request))
                .containsEntry("frequency", GroupExpenseService.FREQUENCY_NOT_ALLOWED);
    }

    @Test
    void aRecurringSharedExpenseAcceptsACatalogFrequency() {
        CreateGroupExpenseRequest request = threeWay();
        request.setType("RECURRING");
        request.setFrequency("MONTHLY");

        assertThat(service.create(ANA, GROUP_ID, KEY, request).expense()).isNotNull();
    }

    @Test
    void aMalformedDateIsRejected() {
        CreateGroupExpenseRequest request = threeWay();
        request.setDate("05/11/2026");

        assertThat(fieldErrorsOf(request)).containsEntry("date", "La fecha debe tener el formato aaaa-mm-dd");
    }

    @Test
    void thePayerMustBeAnActiveRegisteredMember() {
        assertThat(fieldErrorsOf(request("10.00", PEDRO_EX_MEMBER, registered(ANA), registered(JUAN))))
                .containsEntry("payerUserId", GroupExpenseService.PAYER_NOT_MEMBER);
        assertThat(fieldErrorsOf(request("10.00", 999L, registered(ANA), registered(JUAN))))
                .containsEntry("payerUserId", GroupExpenseService.PAYER_NOT_MEMBER);
    }

    @Test
    void atLeastOneParticipantIsRequired() {
        assertThat(fieldErrorsOf(request("10.00", ANA)))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_REQUIRED);
    }

    @Test
    void repeatedParticipantsAreRejected() {
        assertThat(fieldErrorsOf(request("10.00", ANA, registered(JUAN), registered(JUAN))))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_REPEATED);
        assertThat(fieldErrorsOf(request("10.00", ANA, simulated(ABUELA), simulated(ABUELA))))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_REPEATED);
    }

    @Test
    void participantsOutsideTheGroupAreRejected() {
        assertThat(fieldErrorsOf(request("10.00", ANA, registered(JUAN), registered(PEDRO_EX_MEMBER))))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_NOT_IN_GROUP);
        assertThat(fieldErrorsOf(request("10.00", ANA, registered(JUAN), simulated(FOREIGN_PROFILE))))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_NOT_IN_GROUP);
        assertThat(fieldErrorsOf(request("10.00", ANA, registered(JUAN),
                new CreateGroupExpenseRequest.Participant("ROBOT", JUAN, null))))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_NOT_IN_GROUP);
        assertThat(fieldErrorsOf(request("10.00", ANA, registered(JUAN),
                new CreateGroupExpenseRequest.Participant("REGISTERED", null, ABUELA))))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_NOT_IN_GROUP);
        assertThat(fieldErrorsOf(request("10.00", ANA, registered(JUAN), null)))
                .containsEntry("participants", GroupExpenseService.PARTICIPANTS_NOT_IN_GROUP);
    }

    @Test
    void theAmountMustGiveAtLeastOneCentPerParticipant() {
        assertThat(fieldErrorsOf(request("0.02", ANA, registered(ANA), registered(JUAN), simulated(ABUELA))))
                .containsEntry("amount", "El monto no alcanza para dividirlo entre 3 participantes");
    }

    @Test
    void theSplitMethodDefaultsToEqualForLegacyPayloads() {
        GroupExpenseResponse response = service.create(ANA, GROUP_ID, KEY, threeWay()).expense();

        assertThat(response.splitMethod()).isEqualTo("EQUAL");
        ArgumentCaptor<GroupExpense> captor = ArgumentCaptor.forClass(GroupExpense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getSplitMethod()).isEqualTo(SplitMethod.EQUAL);
    }

    @Test
    void customSplitKeepsTheRequestedSharesAndPersistsTheMethod() {
        GroupExpenseResponse response = service.create(ANA, GROUP_ID, KEY, customThreeWay()).expense();

        assertThat(response.splitMethod()).isEqualTo("CUSTOM");
        assertThat(response.shares()).extracting(GroupExpenseResponse.Share::amount)
                .containsExactly("3000.00", "6000.00", "1000.00");
        assertThat(response.debts()).extracting(GroupExpenseResponse.DebtView::amount)
                .containsExactly("6000.00", "1000.00");
        ArgumentCaptor<GroupExpense> captor = ArgumentCaptor.forClass(GroupExpense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getSplitMethod()).isEqualTo(SplitMethod.CUSTOM);
    }

    @Test
    void customSplitAmountsMustSumTheTotalExactly() {
        assertThat(fieldErrorsOf(splitRequest("10000.00", ANA, "CUSTOM",
                registeredAmount(ANA, "3000.00"), registeredAmount(JUAN, "6000.00"),
                simulatedAmount(ABUELA, "999.99"))))
                .containsEntry("participants", GroupExpenseService.CUSTOM_SUM_INVALID);
    }

    @Test
    void customSplitRejectsMissingOrNonPositiveAmounts() {
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "CUSTOM",
                registeredAmount(ANA, "30.00"), registeredAmount(JUAN, "70.00"), simulated(ABUELA))))
                .containsEntry("participants", GroupExpenseService.CUSTOM_AMOUNT_INVALID);
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "CUSTOM",
                registeredAmount(ANA, "30.00"), registeredAmount(JUAN, "70.00"),
                simulatedAmount(ABUELA, "0.00"))))
                .containsEntry("participants", GroupExpenseService.CUSTOM_AMOUNT_INVALID);
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "CUSTOM",
                registeredAmount(ANA, "30.00"), registeredAmount(JUAN, "69.999"),
                simulatedAmount(ABUELA, "0.001"))))
                .containsEntry("participants", GroupExpenseService.CUSTOM_AMOUNT_INVALID);
    }

    @Test
    void percentageSplitFloorsNonPayersAndThePayerAbsorbsTheRemainder() {
        GroupExpenseResponse response = service.create(ANA, GROUP_ID, KEY,
                splitRequest("0.05", ANA, "PERCENTAGE",
                        registeredPercentage(ANA, "33.33"), registeredPercentage(JUAN, "33.33"),
                        simulatedPercentage(ABUELA, "33.34"))).expense();

        assertThat(response.splitMethod()).isEqualTo("PERCENTAGE");
        assertThat(response.shares()).extracting(GroupExpenseResponse.Share::amount)
                .containsExactly("0.03", "0.01", "0.01");
        assertThat(response.debts()).extracting(GroupExpenseResponse.DebtView::amount)
                .containsExactly("0.01", "0.01");
    }

    @Test
    void percentageSplitThatWouldGiveAShareBelowOneCentIsRejected() {
        assertThat(fieldErrorsOf(splitRequest("0.02", ANA, "PERCENTAGE",
                registeredPercentage(ANA, "33.33"), registeredPercentage(JUAN, "33.33"),
                simulatedPercentage(ABUELA, "33.34"))))
                .containsEntry("amount", "El monto no alcanza para dividirlo entre 3 participantes");
    }

    @Test
    void percentageValuesMustBePositiveAndSumExactlyOneHundred() {
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "PERCENTAGE",
                registeredPercentage(ANA, "0"), registeredPercentage(JUAN, "50"),
                simulatedPercentage(ABUELA, "50"))))
                .containsEntry("participants", GroupExpenseService.PERCENTAGE_INVALID);
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "PERCENTAGE",
                registeredPercentage(ANA, "33.33"), registeredPercentage(JUAN, "33.33"),
                simulatedPercentage(ABUELA, "33.33"))))
                .containsEntry("participants", GroupExpenseService.PERCENTAGE_SUM_INVALID);
    }

    @Test
    void equalSplitDoesNotAcceptPerParticipantAmountsOrPercentages() {
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "EQUAL",
                registeredAmount(ANA, "50.00"), registeredPercentage(JUAN, "50"), simulated(ABUELA))))
                .containsEntry("participants", GroupExpenseService.EQUAL_NO_SPLIT_VALUES);
    }

    @Test
    void aParticipantCannotCarryAnAmountAndAPercentageAtTheSameTime() {
        CreateGroupExpenseRequest.Participant mixed =
                new CreateGroupExpenseRequest.Participant("REGISTERED", ANA, null, "30.00", "30");

        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "CUSTOM", mixed,
                registeredAmount(JUAN, "30.00"), simulatedAmount(ABUELA, "40.00"))))
                .containsEntry("participants", GroupExpenseService.MIXED_SPLIT_VALUES);
    }

    @Test
    void customAndPercentageRequireThePayerAmongTheParticipants() {
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "CUSTOM",
                registeredAmount(JUAN, "60.00"), simulatedAmount(ABUELA, "40.00"))))
                .containsEntry("participants", GroupExpenseService.PAYER_MUST_PARTICIPATE);
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "PERCENTAGE",
                registeredPercentage(JUAN, "60"), simulatedPercentage(ABUELA, "40"))))
                .containsEntry("participants", GroupExpenseService.PAYER_MUST_PARTICIPATE);
    }

    @Test
    void anUnknownSplitMethodIsRejected() {
        assertThat(fieldErrorsOf(splitRequest("100.00", ANA, "HALF",
                registered(ANA), registered(JUAN), simulated(ABUELA))))
                .containsEntry("splitMethod", GroupExpenseService.SPLIT_METHOD_INVALID);
    }

    @Test
    void unknownPropertiesAreRejectedOnTheirOwnField() {
        CreateGroupExpenseRequest request = threeWay();
        request.putUnknownProperty("category", "FOOD");

        assertThat(fieldErrorsOf(request)).containsEntry("category", GroupExpenseService.UNKNOWN_PROPERTY);
    }

    @Test
    void aNonMemberGetsTheNeutralNotFound() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, 99L)).thenReturn(Optional.empty());

        var exception = catchThrowableOfType(() -> service.create(99L, GROUP_ID, KEY, threeWay()),
                GroupNotFoundException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo("No existe el grupo solicitado o no sos miembro");
        nothingWasSaved();
    }

    @Test
    void anExMemberGetsTheNeutralNotFound() {
        var exception = catchThrowableOfType(
                () -> service.create(PEDRO_EX_MEMBER, GROUP_ID, KEY, threeWay()), GroupNotFoundException.class);

        assertThat(exception).isNotNull();
        nothingWasSaved();
    }

    @Test
    void aMemberCannotCreateExpensesWhenTheCasillaForbidsIt() {
        permissions.setMembersCannotMakeExpenses(true);

        var exception = catchThrowableOfType(() -> service.create(JUAN, GROUP_ID, KEY,
                request("10.00", JUAN, registered(JUAN), registered(ANA))), GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(GroupExpenseService.CANNOT_CREATE);
        nothingWasSaved();
    }

    @Test
    void anAdminCreatesExpensesEvenWhenMembersCannot() {
        permissions.setMembersCannotMakeExpenses(true);

        assertThat(service.create(ANA, GROUP_ID, KEY, threeWay()).created()).isTrue();
    }

    @Test
    void aMemberOnlyRegistersExpensesPaidByHimselfByDefault() {
        var exception = catchThrowableOfType(() -> service.create(JUAN, GROUP_ID, KEY,
                request("10.00", ANA, registered(JUAN), registered(ANA))), GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(GroupExpenseService.ONLY_OWN_PAYMENTS);
        nothingWasSaved();

        assertThat(service.create(JUAN, GROUP_ID, KEY,
                request("10.00", JUAN, registered(JUAN), registered(ANA))).created()).isTrue();
    }

    @Test
    void aMemberRegistersExpensesPaidByOthersWhenTheCasillaAllowsIt() {
        permissions.setMembersCanGenerateExpensesForOthers(true);

        GroupExpenseResponse response = service.create(JUAN, GROUP_ID, KEY,
                request("10.00", ANA, registered(JUAN), registered(ANA))).expense();

        assertThat(response.payer().userId()).isEqualTo(ANA);
        assertThat(response.createdBy()).isEqualTo(new GroupExpenseResponse.Person(JUAN, "Juan Pérez"));
        verify(activityEventService).recordExpenseCreated(GROUP_ID, JUAN,
                new ExpenseActivityDetails(12L, new BigDecimal("10.00"), "ARS", "Súper", ANA, "Ana López"));
    }

    private GroupExpense existingThreeWay() {
        GroupExpense existing = new GroupExpense();
        existing.setCategory(com.amano.backend.expense.ExpenseCategory.FOOD);
        existing.setExpenseType(com.amano.backend.expense.ExpenseType.ONE_TIME);
        existing.setPaymentMethod(com.amano.backend.expense.PaymentMethod.CASH);
        ReflectionTestUtils.setField(existing, "id", 12L);
        existing.setGroupId(GROUP_ID);
        existing.setCreatedByUserId(ANA);
        existing.setPayerUserId(ANA);
        existing.setAmount(new BigDecimal("10000.00"));
        existing.setCurrency(com.amano.backend.user.Currency.ARS);
        existing.setExpenseDate(LocalDate.of(2026, 11, 5));
        existing.setDescription("Súper");
        existing.setIdempotencyKey(KEY);
        when(expenseRepository.findByCreatedByUserIdAndIdempotencyKey(ANA, KEY)).thenReturn(Optional.of(existing));
        List<GroupExpenseShare> shares = List.of(
                GroupExpenseShare.registered(12L, 0, ANA, "Ana López", new BigDecimal("3333.34")),
                GroupExpenseShare.registered(12L, 1, JUAN, "Juan Pérez", new BigDecimal("3333.33")),
                GroupExpenseShare.simulated(12L, 2, ABUELA, "Abuela", new BigDecimal("3333.33")));
        for (int i = 0; i < shares.size(); i++) {
            ReflectionTestUtils.setField(shares.get(i), "id", 40L + i);
        }
        when(shareRepository.findByExpenseIdOrderByPositionAsc(12L)).thenReturn(shares);
        when(debtRepository.findByExpenseId(12L)).thenReturn(List.of(
                Debt.pendingFor(existing, shares.get(1)), Debt.pendingFor(existing, shares.get(2))));
        return existing;
    }

    private GroupExpense existingCustom() {
        GroupExpense existing = new GroupExpense();
        existing.setCategory(com.amano.backend.expense.ExpenseCategory.FOOD);
        existing.setExpenseType(com.amano.backend.expense.ExpenseType.ONE_TIME);
        existing.setPaymentMethod(com.amano.backend.expense.PaymentMethod.CASH);
        ReflectionTestUtils.setField(existing, "id", 12L);
        existing.setGroupId(GROUP_ID);
        existing.setCreatedByUserId(ANA);
        existing.setPayerUserId(ANA);
        existing.setAmount(new BigDecimal("10000.00"));
        existing.setConvertedAmount(new BigDecimal("10000.00"));
        existing.setCurrency(com.amano.backend.user.Currency.ARS);
        existing.setExpenseDate(LocalDate.of(2026, 11, 5));
        existing.setDescription("Súper");
        existing.setIdempotencyKey(KEY);
        existing.setSplitMethod(SplitMethod.CUSTOM);
        when(expenseRepository.findByCreatedByUserIdAndIdempotencyKey(ANA, KEY)).thenReturn(Optional.of(existing));
        List<GroupExpenseShare> shares = List.of(
                GroupExpenseShare.registered(12L, 0, ANA, "Ana López", new BigDecimal("3000.00")),
                GroupExpenseShare.registered(12L, 1, JUAN, "Juan Pérez", new BigDecimal("6000.00")),
                GroupExpenseShare.simulated(12L, 2, ABUELA, "Abuela", new BigDecimal("1000.00")));
        for (int i = 0; i < shares.size(); i++) {
            ReflectionTestUtils.setField(shares.get(i), "id", 40L + i);
        }
        when(shareRepository.findByExpenseIdOrderByPositionAsc(12L)).thenReturn(shares);
        when(debtRepository.findByExpenseId(12L)).thenReturn(List.of(
                Debt.pendingFor(existing, shares.get(1)), Debt.pendingFor(existing, shares.get(2))));
        return existing;
    }

    @Test
    void theSameKeyAndPayloadReplaysTheExistingExpense() {
        existingThreeWay();

        GroupExpenseService.Registration registration = service.create(ANA, GROUP_ID, KEY, threeWay());

        assertThat(registration.created()).isFalse();
        assertThat(registration.expense().id()).isEqualTo(12L);
        assertThat(registration.expense().debts()).hasSize(2);
        nothingWasSaved();
    }

    @Test
    void theSameKeyWithAnotherPayloadIsAConflict() {
        existingThreeWay();

        assertThat(catchThrowableOfType(() -> service.create(ANA, GROUP_ID, KEY,
                request("9999.99", ANA, registered(ANA), registered(JUAN), simulated(ABUELA))),
                IdempotencyConflictException.class)).isNotNull();
        assertThat(catchThrowableOfType(() -> service.create(ANA, GROUP_ID, KEY,
                request("10000.00", ANA, registered(JUAN), registered(ANA), simulated(ABUELA))),
                IdempotencyConflictException.class)).isNotNull();
        nothingWasSaved();
    }

    @Test
    void theSameKeyAndCustomPayloadReplaysTheExistingExpense() {
        existingCustom();

        GroupExpenseService.Registration registration = service.create(ANA, GROUP_ID, KEY, customThreeWay());

        assertThat(registration.created()).isFalse();
        assertThat(registration.expense().splitMethod()).isEqualTo("CUSTOM");
        nothingWasSaved();
    }

    @Test
    void theSameKeyWithAnotherSplitMethodOrDifferentAmountsIsAConflict() {
        existingCustom();

        assertThat(catchThrowableOfType(() -> service.create(ANA, GROUP_ID, KEY,
                request("10000.00", ANA, registered(ANA), registered(JUAN), simulated(ABUELA))),
                IdempotencyConflictException.class)).isNotNull();
        assertThat(catchThrowableOfType(() -> service.create(ANA, GROUP_ID, KEY,
                splitRequest("10000.00", ANA, "CUSTOM",
                        registeredAmount(ANA, "3000.00"), registeredAmount(JUAN, "5000.00"),
                        simulatedAmount(ABUELA, "2000.00"))),
                IdempotencyConflictException.class)).isNotNull();
        nothingWasSaved();
    }

    @Test
    void theConcurrentRaceIsResolvedAgainstThePersistedWinner() {
        existingThreeWay();

        GroupExpenseService.Registration registration =
                service.resolveConcurrentRegistration(ANA, GROUP_ID, KEY, threeWay());

        assertThat(registration.created()).isFalse();
        assertThat(registration.expense().id()).isEqualTo(12L);
    }

    @Test
    void theConcurrentRaceWithoutAWinnerIsAnError() {
        assertThat(catchThrowableOfType(() -> service.resolveConcurrentRegistration(ANA, GROUP_ID, KEY, threeWay()),
                IllegalStateException.class)).isNotNull();
    }

    @Test
    void theDetailShowsSharesInOrderAndDebtsWithTheirStatus() {
        GroupExpense existing = existingThreeWay();
        when(expenseRepository.findByIdAndGroupId(12L, GROUP_ID)).thenReturn(Optional.of(existing));

        GroupExpenseResponse response = service.get(JUAN, GROUP_ID, 12L);

        assertThat(response.shares()).extracting(GroupExpenseResponse.Share::position).containsExactly(0, 1, 2);
        assertThat(response.debts()).extracting(debt -> debt.debtor().name())
                .containsExactly("Juan Pérez", "Abuela");
        assertThat(response.debts().get(1).debtor().type()).isEqualTo("SIMULATED");
        assertThat(response.debts().get(1).creditor()).isEqualTo(new GroupExpenseResponse.Person(ANA, "Ana López"));
    }

    @Test
    void anExpenseOfAnotherGroupIsNotFound() {
        when(expenseRepository.findByIdAndGroupId(anyLong(), eq(GROUP_ID))).thenReturn(Optional.empty());

        assertThat(catchThrowableOfType(() -> service.get(ANA, GROUP_ID, 12L), GroupNotFoundException.class))
                .isNotNull();
    }

    @Test
    void aNonMemberCannotSeeTheDetail() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, 99L)).thenReturn(Optional.empty());

        assertThat(catchThrowableOfType(() -> service.get(99L, GROUP_ID, 12L), GroupNotFoundException.class))
                .isNotNull();
        verify(expenseRepository, never()).findByIdAndGroupId(any(), any());
    }

    // 018 T034 (FR-016): canRegisterPayment per debt for the session user.
    @Test
    void theDetailTellsWhoCanRegisterAPaymentOfEachDebt() {
        GroupExpense existing = existingThreeWay();
        when(expenseRepository.findByIdAndGroupId(12L, GROUP_ID)).thenReturn(Optional.of(existing));
        long carla = 30L;
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, carla))
                .thenReturn(Optional.of(new GroupMember(carla, GROUP_ID, GroupMember.ROLE_MEMBER)));

        assertThat(service.get(JUAN, GROUP_ID, 12L).debts())
                .extracting(GroupExpenseResponse.DebtView::canRegisterPayment).containsExactly(true, false);
        assertThat(service.get(ANA, GROUP_ID, 12L).debts())
                .extracting(GroupExpenseResponse.DebtView::canRegisterPayment).containsExactly(true, true);
        assertThat(service.get(carla, GROUP_ID, 12L).debts())
                .extracting(GroupExpenseResponse.DebtView::canRegisterPayment).containsExactly(false, false);
    }

    @Test
    void aPaidDebtCannotReceiveMorePayments() {
        GroupExpense existing = existingThreeWay();
        when(expenseRepository.findByIdAndGroupId(12L, GROUP_ID)).thenReturn(Optional.of(existing));
        List<Debt> debts = debtRepository.findByExpenseId(12L);
        debts.get(0).applyPayment(new BigDecimal("3333.33"));

        GroupExpenseResponse.DebtView paid = service.get(ANA, GROUP_ID, 12L).debts().get(0);

        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.canRegisterPayment()).isFalse();
    }
}
