package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.expense.ExpenseCategory;
import com.amano.backend.expense.ExpenseType;
import com.amano.backend.expense.PaymentMethod;
import com.amano.backend.expense.RecurrenceFrequency;
import com.amano.backend.expense.RecurrenceStatus;
import com.amano.backend.expense.RecurringExpenseFinishedException;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.ExpenseActivityDetails;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupActivity;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupRepository;
import com.amano.backend.user.Currency;

/**
 * Unit tests of {@link GroupRecurringExpenseService} (029): state machine,
 * re-anchoring on resume and activity events. Fixed clock on 24/10/2026.
 */
class GroupRecurringExpenseServiceTest {

    private static final Long CALLER = 10L;
    private static final Long OTHER = 20L;
    private static final Long GROUP = 1L;
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-24");

    private GroupExpenseRepository expenseRepository;
    private GroupExpenseShareRepository shareRepository;
    private DebtRepository debtRepository;
    private GroupRepository groupRepository;
    private GroupMemberRepository groupMemberRepository;
    private ActivityEventService activityEventService;
    private GroupRecurringExpenseService service;
    private final List<GroupExpense> saved = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        expenseRepository = mock(GroupExpenseRepository.class);
        shareRepository = mock(GroupExpenseShareRepository.class);
        debtRepository = mock(DebtRepository.class);
        groupRepository = mock(GroupRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        activityEventService = mock(ActivityEventService.class);
        Clock clock = Clock.fixed(TODAY.atTime(15, 0).atZone(ZONE).toInstant(), ZONE);
        service = new GroupRecurringExpenseService(expenseRepository, shareRepository, debtRepository,
                groupRepository, groupMemberRepository, activityEventService, clock);
        saved.clear();
        idSequence.set(1);

        when(expenseRepository.save(any(GroupExpense.class))).thenAnswer(invocation -> {
            GroupExpense expense = invocation.getArgument(0);
            if (expense.getId() == null) {
                ReflectionTestUtils.setField(expense, "id", idSequence.incrementAndGet());
            }
            saved.add(expense);
            return expense;
        });
        when(shareRepository.save(any(GroupExpenseShare.class))).thenAnswer(invocation ->
                invocation.getArgument(0));
        when(groupRepository.findById(GROUP)).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP, CALLER))
                .thenReturn(Optional.of(member(GroupMember.ROLE_MEMBER)));
        when(debtRepository.findByExpenseId(any())).thenReturn(List.of());
    }

    private Group group() {
        Group group = new Group();
        ReflectionTestUtils.setField(group, "id", GROUP);
        group.setPrimaryCurrency(Currency.ARS);
        group.setDebtDueDays(7);
        return group;
    }

    private GroupMember member(String role) {
        GroupMember member = new GroupMember();
        member.setRole(role);
        return member;
    }

    private GroupExpense series(long id, RecurrenceFrequency frequency, RecurrenceStatus status,
            String expenseDate, String anchorDate, String nextDate) {
        GroupExpense expense = new GroupExpense();
        ReflectionTestUtils.setField(expense, "id", id);
        expense.setGroupId(GROUP);
        expense.setCreatedByUserId(CALLER);
        expense.setPayerUserId(CALLER);
        expense.setAmount(new BigDecimal("10000.00"));
        expense.setCurrency(Currency.ARS);
        expense.setExpenseDate(LocalDate.parse(expenseDate));
        expense.setDescription("Alquiler");
        expense.setIdempotencyKey(UUID.randomUUID());
        expense.setExpenseType(ExpenseType.RECURRING);
        expense.setCategory(ExpenseCategory.HOUSING);
        expense.setPaymentMethod(PaymentMethod.TRANSFER);
        expense.setRecurrenceFrequency(frequency);
        expense.setRecurrenceStatus(status);
        expense.setRecurrenceAnchorDate(LocalDate.parse(anchorDate));
        expense.setRecurrenceNextDate(nextDate == null ? null : LocalDate.parse(nextDate));
        return expense;
    }

    private List<GroupExpenseShare> shares(Long seriesId) {
        return List.of(
                GroupExpenseShare.registered(seriesId, 0, CALLER, "Ana", new BigDecimal("5000.00")),
                GroupExpenseShare.registered(seriesId, 1, OTHER, "Juan", new BigDecimal("5000.00")));
    }

    private void owns(long id, GroupExpense expense) {
        when(expenseRepository.findByIdAndGroupId(id, GROUP)).thenReturn(Optional.of(expense));
    }

    private List<GroupExpense> occurrences() {
        return saved.stream().filter(e -> e.getRecurrenceSourceId() != null).toList();
    }

    @Test
    void pausingKeepsNextDateInDatabaseButResponseHidesIt() {
        GroupExpense active = series(100, RecurrenceFrequency.MONTHLY, RecurrenceStatus.ACTIVE,
                "2026-08-05", "2026-08-05", "2026-11-05");
        owns(100, active);
        when(shareRepository.findByExpenseIdOrderByPositionAsc(100L)).thenReturn(shares(100L));

        RecurringGroupExpenseResponse response = service.updateSeries(CALLER, GROUP, 100L,
                Map.of("status", "PAUSED"));

        assertThat(active.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.PAUSED);
        assertThat(active.getRecurrenceNextDate()).isEqualTo(LocalDate.parse("2026-11-05"));
        assertThat(response.status()).isEqualTo("PAUSED");
        assertThat(response.nextOccurrenceDate()).isNull();
        verify(activityEventService).recordRecurrenceChange(GROUP, CALLER,
                GroupActivity.GroupActivityType.RECURRENCE_PAUSED,
                new RecurringActivityDetails(100L, "Alquiler", "PAUSED"));
    }

    @Test
    void resumingReanchorsAndCreatesImmediateOccurrence() {
        GroupExpense paused = series(100, RecurrenceFrequency.WEEKLY, RecurrenceStatus.PAUSED,
                "2026-08-05", "2026-08-05", "2026-09-05");
        owns(100, paused);
        when(shareRepository.findByExpenseIdOrderByPositionAsc(100L)).thenReturn(shares(100L));
        when(expenseRepository.findByRecurrenceSourceIdAndExpenseDate(100L, TODAY))
                .thenReturn(Optional.empty());

        RecurringGroupExpenseResponse response = service.updateSeries(CALLER, GROUP, 100L,
                Map.of("status", "ACTIVE"));

        assertThat(paused.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.ACTIVE);
        assertThat(paused.getRecurrenceAnchorDate()).isEqualTo(TODAY);
        assertThat(paused.getRecurrenceNextDate()).isEqualTo(TODAY.plusWeeks(1));
        assertThat(response.nextOccurrenceDate()).isEqualTo(TODAY.plusWeeks(1).toString());
        assertThat(occurrences()).hasSize(1);
        GroupExpense immediate = occurrences().get(0);
        assertThat(immediate.getExpenseDate()).isEqualTo(TODAY);
        assertThat(immediate.getDueDate()).isEqualTo(TODAY.plusDays(7));
        assertThat(immediate.getRecurrenceCreatedVia()).isEqualTo("RESUME");
        verify(activityEventService).recordRecurrenceChange(GROUP, CALLER,
                GroupActivity.GroupActivityType.RECURRENCE_RESUMED,
                new RecurringActivityDetails(100L, "Alquiler", "ACTIVE"));
        verify(activityEventService).recordExpenseCreated(eq(GROUP), eq(CALLER), any(ExpenseActivityDetails.class));
    }

    @Test
    void resumingWhenOccurrenceAlreadyExistsTodayDoesNotDuplicate() {
        GroupExpense paused = series(100, RecurrenceFrequency.WEEKLY, RecurrenceStatus.PAUSED,
                "2026-08-05", "2026-08-05", "2026-09-05");
        owns(100, paused);
        GroupExpense existing = new GroupExpense();
        ReflectionTestUtils.setField(existing, "id", 999L);
        existing.setRecurrenceSourceId(100L);
        existing.setExpenseDate(TODAY);
        when(expenseRepository.findByRecurrenceSourceIdAndExpenseDate(100L, TODAY))
                .thenReturn(Optional.of(existing));

        service.updateSeries(CALLER, GROUP, 100L, Map.of("status", "ACTIVE"));

        assertThat(occurrences()).isEmpty();
        assertThat(paused.getRecurrenceAnchorDate()).isEqualTo(TODAY);
        verify(activityEventService).recordRecurrenceChange(GROUP, CALLER,
                GroupActivity.GroupActivityType.RECURRENCE_RESUMED,
                new RecurringActivityDetails(100L, "Alquiler", "ACTIVE"));
        verify(activityEventService, never()).recordExpenseCreated(anyLong(), anyLong(), any(ExpenseActivityDetails.class));
    }

    @Test
    void resumingAnActiveSeriesIsANoOp() {
        GroupExpense active = series(100, RecurrenceFrequency.MONTHLY, RecurrenceStatus.ACTIVE,
                "2026-08-05", "2026-08-05", "2026-11-05");
        owns(100, active);

        service.updateSeries(CALLER, GROUP, 100L, Map.of("status", "ACTIVE"));

        assertThat(active.getRecurrenceAnchorDate()).isEqualTo(LocalDate.parse("2026-08-05"));
        assertThat(occurrences()).isEmpty();
        verifyNoInteractions(activityEventService);
    }

    @Test
    void finishingClearsNextDate() {
        GroupExpense active = series(100, RecurrenceFrequency.MONTHLY, RecurrenceStatus.ACTIVE,
                "2026-08-05", "2026-08-05", "2026-11-05");
        owns(100, active);

        RecurringGroupExpenseResponse response = service.updateSeries(CALLER, GROUP, 100L,
                Map.of("status", "FINISHED"));

        assertThat(active.getRecurrenceStatus()).isEqualTo(RecurrenceStatus.FINISHED);
        assertThat(active.getRecurrenceNextDate()).isNull();
        assertThat(response.nextOccurrenceDate()).isNull();
        verify(activityEventService).recordRecurrenceChange(GROUP, CALLER,
                GroupActivity.GroupActivityType.RECURRENCE_FINISHED,
                new RecurringActivityDetails(100L, "Alquiler", "FINISHED"));
    }

    @Test
    void modifyingAFinishedSeriesThrows() {
        GroupExpense finished = series(100, RecurrenceFrequency.MONTHLY, RecurrenceStatus.FINISHED,
                "2026-08-05", "2026-08-05", null);
        owns(100, finished);

        for (String target : List.of("ACTIVE", "PAUSED", "FINISHED")) {
            assertThatThrownBy(() -> service.updateSeries(CALLER, GROUP, 100L, Map.of("status", target)))
                    .isInstanceOf(RecurringExpenseFinishedException.class)
                    .hasMessage("El gasto recurrente ya está finalizado y no puede modificarse");
        }
        assertThatThrownBy(() -> service.updateSeries(CALLER, GROUP, 100L, Map.of("frequency", "YEARLY")))
                .isInstanceOf(RecurringExpenseFinishedException.class);
        verifyNoInteractions(activityEventService);
    }

    @Test
    void jobUsesTheAnchorDateToAdvanceOccurrences() {
        // Anchor moved to 2026-10-24 (resume), next date 2026-11-24; job on 2026-11-13
        // should not generate because the next date is outside the 7-day window.
        GroupExpense active = series(100, RecurrenceFrequency.MONTHLY, RecurrenceStatus.ACTIVE,
                "2026-08-05", "2026-10-24", "2026-11-24");
        when(expenseRepository.findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc(
                RecurrenceStatus.ACTIVE, TODAY.plusDays(7))).thenReturn(List.of(active));
        when(shareRepository.findByExpenseIdOrderByPositionAsc(100L)).thenReturn(shares(100L));

        int created = service.generateDueOccurrences(TODAY);

        assertThat(created).isZero();
        assertThat(occurrences()).isEmpty();
    }
}
