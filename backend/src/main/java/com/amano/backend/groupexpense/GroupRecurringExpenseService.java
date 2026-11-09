package com.amano.backend.groupexpense;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.expense.RecurrenceStatus;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.ExpenseActivityDetails;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupRepository;
import com.amano.backend.user.Currency;

/**
 * Generates the due occurrences of recurring shared expenses (V32), mirroring
 * {@code RecurringExpenseService} for personal expenses. A series keeps its
 * split, payer, classification and conversion data; each occurrence copies the
 * series shares and creates its own PENDING debts, so payments and reminders are
 * tracked per occurrence. The deterministic idempotency key plus
 * {@code uq_group_expenses_recurrence_occurrence} prevent duplicates when the
 * job runs twice.
 */
@Service
public class GroupRecurringExpenseService {

    private static final Logger log = LoggerFactory.getLogger(GroupRecurringExpenseService.class);

    /** Safety bound per series and run, as in personal recurrences. */
    private static final int MAX_OCCURRENCES_PER_RUN = 1000;

    /**
     * 028 FR-006: occurrences are generated up to this many days before their
     * own date, so their debts already exist when 025 schedules the reminder
     * ({@code dueDate - reminderSendDays}). It matches the largest option of the
     * reminder-send catalog (7 days).
     */
    private static final int ANTICIPATION_DAYS = 7;

    private final GroupExpenseRepository expenseRepository;
    private final GroupExpenseShareRepository shareRepository;
    private final DebtRepository debtRepository;
    private final GroupRepository groupRepository;
    private final ActivityEventService activityEventService;
    private final Clock clock;

    public GroupRecurringExpenseService(GroupExpenseRepository expenseRepository,
            GroupExpenseShareRepository shareRepository,
            DebtRepository debtRepository,
            GroupRepository groupRepository,
            ActivityEventService activityEventService,
            Clock clock) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.debtRepository = debtRepository;
        this.groupRepository = groupRepository;
        this.activityEventService = activityEventService;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Generates every due occurrence of the ACTIVE series up to the end of the
     * anticipation window (028 FR-006) and advances their next date. Returns
     * how many were created.
     */
    @Transactional
    public int generateDueOccurrences(LocalDate today) {
        LocalDate windowEnd = today.plusDays(ANTICIPATION_DAYS);
        int created = 0;
        List<GroupExpense> due = expenseRepository
                .findByRecurrenceStatusAndRecurrenceNextDateLessThanEqualOrderByIdAsc(
                        RecurrenceStatus.ACTIVE, windowEnd);
        for (GroupExpense series : due) {
            int generated = 0;
            LocalDate date = series.getRecurrenceNextDate();
            boolean advanced = false;
            while (date != null && !date.isAfter(windowEnd) && generated < MAX_OCCURRENCES_PER_RUN) {
                advanced = true;
                if (expenseRepository.findByRecurrenceSourceIdAndExpenseDate(series.getId(), date).isEmpty()) {
                    createOccurrence(series, date);
                    generated++;
                }
                date = series.getRecurrenceFrequency().firstAfter(series.getExpenseDate(), date);
            }
            if (advanced) {
                series.setRecurrenceNextDate(date);
                expenseRepository.save(series);
                created += generated;
            }
        }
        if (created > 0) {
            log.info("Group recurring expenses: {} occurrence(s) generated for {} series", created, due.size());
        }
        return created;
    }

    private void createOccurrence(GroupExpense series, LocalDate date) {
        GroupExpense occurrence = new GroupExpense();
        occurrence.setGroupId(series.getGroupId());
        occurrence.setFriendUserId(series.getFriendUserId());
        occurrence.setCreatedByUserId(series.getCreatedByUserId());
        occurrence.setPayerUserId(series.getPayerUserId());
        occurrence.setAmount(series.getAmount());
        occurrence.setCurrency(series.getCurrency());
        occurrence.setConvertedAmount(series.getConvertedAmount());
        occurrence.setExchangeRate(series.getExchangeRate());
        occurrence.setRateDate(series.getRateDate());
        occurrence.setRateSource(series.getRateSource());
        occurrence.setSplitMethod(series.getSplitMethod());
        occurrence.setCategory(series.getCategory());
        occurrence.setExpenseType(series.getExpenseType());
        occurrence.setPaymentMethod(series.getPaymentMethod());
        occurrence.setExpenseDate(date);
        occurrence.setDueDate(date);
        occurrence.setDescription(series.getDescription());
        occurrence.setIdempotencyKey(occurrenceKey(series.getId(), date));
        occurrence.setRecurrenceSourceId(series.getId());
        GroupExpense saved = expenseRepository.save(occurrence);

        List<GroupExpenseShare> seriesShares =
                shareRepository.findByExpenseIdOrderByPositionAsc(series.getId());
        List<GroupExpenseShare> occurrences = new ArrayList<>();
        for (GroupExpenseShare share : seriesShares) {
            GroupExpenseShare copy = share.getParticipantType() == ParticipantType.REGISTERED
                    ? GroupExpenseShare.registered(saved.getId(), share.getPosition(), share.getUserId(),
                            share.getParticipantName(), share.getAmount())
                    : GroupExpenseShare.simulated(saved.getId(), share.getPosition(),
                            share.getSimulatedProfileId(), share.getParticipantName(), share.getAmount());
            occurrences.add(shareRepository.save(copy));
        }

        Currency debtCurrency = debtCurrencyOf(series);
        List<Debt> debts = new ArrayList<>();
        for (GroupExpenseShare share : occurrences) {
            if (!share.isOf(saved.getPayerUserId())) {
                debts.add(Debt.pendingFor(saved, share, debtCurrency));
            }
        }
        debtRepository.saveAll(debts);

        if (saved.getGroupId() != null) {
            String payerName = seriesShares.stream()
                    .filter(share -> share.isOf(saved.getPayerUserId()))
                    .map(GroupExpenseShare::getParticipantName)
                    .findFirst()
                    .orElse(null);
            activityEventService.recordExpenseCreated(saved.getGroupId(), saved.getCreatedByUserId(),
                    new ExpenseActivityDetails(saved.getId(), saved.getAmount(),
                            saved.getCurrency().name(), saved.getDescription(), saved.getPayerUserId(),
                            payerName));
        }
    }

    /** Debt currency of the series: its existing debts or the group primary currency. */
    private Currency debtCurrencyOf(GroupExpense series) {
        List<Debt> seriesDebts = debtRepository.findByExpenseId(series.getId());
        if (!seriesDebts.isEmpty()) {
            return seriesDebts.get(0).getCurrency();
        }
        if (series.getGroupId() == null) {
            return series.getCurrency();
        }
        return groupRepository.findById(series.getGroupId())
                .map(Group::getPrimaryCurrency)
                .orElse(series.getCurrency());
    }

    /** Deterministic key of the occurrence of a series on a date. */
    static UUID occurrenceKey(Long seriesId, LocalDate date) {
        return UUID.nameUUIDFromBytes(
                ("amano-group-recurrence:" + seriesId + ":" + date).getBytes(StandardCharsets.UTF_8));
    }
}
